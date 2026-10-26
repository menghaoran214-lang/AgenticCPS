package com.qiji.cps.module.cps.service.split.calculator;

import cn.hutool.core.lang.Assert;
import com.qiji.cps.framework.common.util.json.JsonUtils;
import com.qiji.cps.module.cps.dal.dataobject.promoter.CpsPromoterRelationDO;
import com.qiji.cps.module.cps.dal.dataobject.split.CpsSplitRuleDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * CPS 多级分账纯计算引擎（纯领域逻辑，无副作用）
 *
 * <p>核心职责：
 * <ul>
 *   <li>净佣金池计算：联盟总佣金先扣减平台留存率（如 20%），剩余部分作为净可分配佣金池</li>
 *   <li>各级比例瓜分：根据自购、一级、二级提成比例从净佣金池拆分金额</li>
 *   <li>上线资格检查：若无上级或上级分销资格被冻结，对应的未分配提成沉淀至平台留存</li>
 *   <li>防超分断言熔断：严格保证 L0 + L1 + L2 <= 净佣金池，总出账 <= 总佣金</li>
 * </ul>
 * </p>
 *
 * @author CPS System
 */
@Slf4j
@Component
public class CpsSplitCalculator {

    private static final BigDecimal DEFAULT_PLATFORM_SHARE_RATE = new BigDecimal("0.2000");
    private static final BigDecimal DEFAULT_SELF_REBATE_RATE = new BigDecimal("0.6000");
    private static final BigDecimal DEFAULT_PARENT_REBATE_RATE = new BigDecimal("0.2500");
    private static final BigDecimal DEFAULT_GRANDPARENT_REBATE_RATE = new BigDecimal("0.1500");

    /**
     * 计算多级分账结果
     *
     * @param commissionCent        外部联盟总佣金（分）
     * @param rule                  命中的分账规则
     * @param buyerRelation         出单人关系链（包含 parentMemberId, grandparentMemberId）
     * @param parentRelation        一级推荐人的关系与资格（为 null 表示未查到或无上线）
     * @param grandparentRelation   二级推荐人的关系与资格（为 null 表示未查到或无上线）
     * @return 分账计算分配结果
     */
    public CpsSplitAllocation calculateSplit(Long commissionCent,
                                            CpsSplitRuleDO rule,
                                            CpsPromoterRelationDO buyerRelation,
                                            CpsPromoterRelationDO parentRelation,
                                            CpsPromoterRelationDO grandparentRelation) {
        if (commissionCent == null || commissionCent <= 0) {
            return buildZeroAllocation(commissionCent, rule);
        }

        BigDecimal platformShareRate = rule != null && rule.getPlatformShareRate() != null
                ? rule.getPlatformShareRate() : DEFAULT_PLATFORM_SHARE_RATE;
        BigDecimal selfRebateRate = rule != null && rule.getSelfRebateRate() != null
                ? rule.getSelfRebateRate() : DEFAULT_SELF_REBATE_RATE;
        BigDecimal parentRebateRate = rule != null && rule.getParentRebateRate() != null
                ? rule.getParentRebateRate() : DEFAULT_PARENT_REBATE_RATE;
        BigDecimal grandparentRebateRate = rule != null && rule.getGrandparentRebateRate() != null
                ? rule.getGrandparentRebateRate() : DEFAULT_GRANDPARENT_REBATE_RATE;

        // 校验合规与比例之和：自购 + 一级 + 二级 比例不能大于 1.0 (100%)
        BigDecimal sumRates = selfRebateRate.add(parentRebateRate).add(grandparentRebateRate);
        Assert.isTrue(sumRates.compareTo(BigDecimal.ONE) <= 0,
                "多级分账规则配置错误：各级分账比例之和不能超过 100%，当前为 " + sumRates);

        // 1. 计算平台留存技术服务费
        long platformShareCent = BigDecimal.valueOf(commissionCent)
                .multiply(platformShareRate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
        if (platformShareCent > commissionCent) {
            platformShareCent = commissionCent;
        }

        // 2. 净可分配佣金池
        long distributableCent = Math.max(0L, commissionCent - platformShareCent);

        // 3. 计算潜在分配金额
        long selfCent = BigDecimal.valueOf(distributableCent)
                .multiply(selfRebateRate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        long parentPotentialCent = BigDecimal.valueOf(distributableCent)
                .multiply(parentRebateRate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        long grandparentPotentialCent = BigDecimal.valueOf(distributableCent)
                .multiply(grandparentRebateRate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        // 4. 舍入误差防溢出保护：三者之和不能超过净佣金池
        long totalPotential = selfCent + parentPotentialCent + grandparentPotentialCent;
        if (totalPotential > distributableCent) {
            long excess = totalPotential - distributableCent;
            if (grandparentPotentialCent >= excess) {
                grandparentPotentialCent -= excess;
            } else if (parentPotentialCent >= excess) {
                parentPotentialCent -= excess;
            } else {
                selfCent = Math.max(0L, selfCent - excess);
            }
        }

        // 5. 资格判定：出单人必须有效；推荐人存在且分销状态正常时才发放提成，否则沉淀至平台
        boolean isParentEligible = buyerRelation != null && buyerRelation.getParentMemberId() != null
                && isPromoterActive(parentRelation);
        boolean isGrandparentEligible = buyerRelation != null && buyerRelation.getGrandparentMemberId() != null
                && isPromoterActive(grandparentRelation);

        long parentRebateCent = isParentEligible ? parentPotentialCent : 0L;
        long grandparentRebateCent = isGrandparentEligible ? grandparentPotentialCent : 0L;

        // 6. 平台沉淀与最终总收益
        long unallocatedToPlatformCent = distributableCent - (selfCent + parentRebateCent + grandparentRebateCent);
        Assert.isTrue(unallocatedToPlatformCent >= 0, "分账计算异常：未分配沉淀金额不能为负数");

        long platformTotalIncomeCent = platformShareCent + unallocatedToPlatformCent;

        // 7. 终极一致性断言（总流出严格等于总佣金）
        Assert.isTrue(selfCent + parentRebateCent + grandparentRebateCent + platformTotalIncomeCent == commissionCent,
                "分账资金守恒断言失败：流出金额与原始佣金不一致");

        // 8. 构造规则快照
        String snapshotJson = buildRuleSnapshot(rule, platformShareRate, selfRebateRate,
                parentRebateRate, grandparentRebateRate);

        return CpsSplitAllocation.builder()
                .commissionCent(commissionCent)
                .platformShareCent(platformShareCent)
                .distributableCent(distributableCent)
                .selfRebateCent(selfCent)
                .parentRebateCent(parentRebateCent)
                .grandparentRebateCent(grandparentRebateCent)
                .partnerSpreadCent(0L) // 预留
                .unallocatedToPlatformCent(unallocatedToPlatformCent)
                .platformTotalIncomeCent(platformTotalIncomeCent)
                .splitRuleId(rule != null ? rule.getId() : null)
                .ruleName(rule != null ? rule.getRuleName() : "默认多级分账规则")
                .platformShareRate(platformShareRate)
                .selfRebateRate(selfRebateRate)
                .parentRebateRate(parentRebateRate)
                .grandparentRebateRate(grandparentRebateRate)
                .ruleSnapshotJson(snapshotJson)
                .build();
    }

    private boolean isPromoterActive(CpsPromoterRelationDO relation) {
        return relation != null && Integer.valueOf(1).equals(relation.getPromoterStatus());
    }

    private CpsSplitAllocation buildZeroAllocation(Long commissionCent, CpsSplitRuleDO rule) {
        long c = commissionCent == null ? 0L : commissionCent;
        return CpsSplitAllocation.builder()
                .commissionCent(c)
                .platformShareCent(0L)
                .distributableCent(0L)
                .selfRebateCent(0L)
                .parentRebateCent(0L)
                .grandparentRebateCent(0L)
                .partnerSpreadCent(0L)
                .unallocatedToPlatformCent(0L)
                .platformTotalIncomeCent(0L)
                .splitRuleId(rule != null ? rule.getId() : null)
                .ruleName(rule != null ? rule.getRuleName() : "零佣金规则")
                .platformShareRate(BigDecimal.ZERO)
                .selfRebateRate(BigDecimal.ZERO)
                .parentRebateRate(BigDecimal.ZERO)
                .grandparentRebateRate(BigDecimal.ZERO)
                .ruleSnapshotJson("{}")
                .build();
    }

    private String buildRuleSnapshot(CpsSplitRuleDO rule,
                                     BigDecimal platformShareRate,
                                     BigDecimal selfRebateRate,
                                     BigDecimal parentRebateRate,
                                     BigDecimal grandparentRebateRate) {
        Map<String, Object> map = new HashMap<>();
        map.put("ruleId", rule != null ? rule.getId() : null);
        map.put("ruleName", rule != null ? rule.getRuleName() : "默认规则");
        map.put("platformShareRate", platformShareRate);
        map.put("selfRebateRate", selfRebateRate);
        map.put("parentRebateRate", parentRebateRate);
        map.put("grandparentRebateRate", grandparentRebateRate);
        map.put("freezeDays", rule != null && rule.getFreezeDays() != null ? rule.getFreezeDays() : 15);
        return JsonUtils.toJsonString(map);
    }

}

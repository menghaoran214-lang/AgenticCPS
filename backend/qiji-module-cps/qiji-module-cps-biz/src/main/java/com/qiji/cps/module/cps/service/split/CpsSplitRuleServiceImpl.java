package com.qiji.cps.module.cps.service.split;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.module.cps.dal.dataobject.split.CpsSplitRuleDO;
import com.qiji.cps.module.cps.dal.mysql.split.CpsSplitRuleMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * CPS 多级分账规则 Service 实现类
 *
 * @author CPS System
 */
@Slf4j
@Service
@Validated
public class CpsSplitRuleServiceImpl implements CpsSplitRuleService {

    @Resource
    private CpsSplitRuleMapper splitRuleMapper;

    @Override
    public CpsSplitRuleDO matchSplitRule(String platformCode, Long memberLevelId) {
        List<CpsSplitRuleDO> activeList = splitRuleMapper.selectActiveList();
        if (CollUtil.isEmpty(activeList)) {
            log.warn("[matchSplitRule] 数据库无可用分账规则，回退至内置默认规则");
            return buildFallbackDefaultRule();
        }

        // 1. 平台 + 等级 双精准匹配
        if (platformCode != null && memberLevelId != null) {
            for (CpsSplitRuleDO rule : activeList) {
                if (Objects.equals(rule.getPlatformCode(), platformCode)
                        && Objects.equals(rule.getMemberLevelId(), memberLevelId)) {
                    return rule;
                }
            }
        }

        // 2. 平台精准匹配（等级通用）
        if (platformCode != null) {
            for (CpsSplitRuleDO rule : activeList) {
                if (Objects.equals(rule.getPlatformCode(), platformCode)
                        && rule.getMemberLevelId() == null) {
                    return rule;
                }
            }
        }

        // 3. 等级精准匹配（平台全通用）
        if (memberLevelId != null) {
            for (CpsSplitRuleDO rule : activeList) {
                if (rule.getPlatformCode() == null
                        && Objects.equals(rule.getMemberLevelId(), memberLevelId)) {
                    return rule;
                }
            }
        }

        // 4. 全局通用兜底规则（平台与等级皆为 null）
        for (CpsSplitRuleDO rule : activeList) {
            if (rule.getPlatformCode() == null && rule.getMemberLevelId() == null) {
                return rule;
            }
        }

        // 5. 若皆未命中，返回第一条可用规则
        return activeList.get(0);
    }

    @Override
    public CpsSplitRuleDO getSplitRule(Long id) {
        if (id == null) {
            return null;
        }
        return splitRuleMapper.selectById(id);
    }

    @Override
    public List<CpsSplitRuleDO> getActiveRuleList() {
        return splitRuleMapper.selectActiveList();
    }

    @Override
    public Long saveSplitRule(CpsSplitRuleDO ruleDO) {
        Assert.notNull(ruleDO, "分账规则不能为空");
        validateRuleRates(ruleDO);
        if (ruleDO.getId() == null) {
            splitRuleMapper.insert(ruleDO);
        } else {
            splitRuleMapper.updateById(ruleDO);
        }
        return ruleDO.getId();
    }

    @Override
    public void deleteSplitRule(Long id) {
        if (id != null) {
            splitRuleMapper.deleteById(id);
        }
    }

    @Override
    public PageResult<CpsSplitRuleDO> getSplitRulePage(com.qiji.cps.module.cps.controller.admin.split.vo.CpsSplitRulePageReqVO reqVO) {
        return splitRuleMapper.selectPage(reqVO);
    }

    private void validateRuleRates(CpsSplitRuleDO rule) {
        BigDecimal selfRate = rule.getSelfRebateRate() != null ? rule.getSelfRebateRate() : BigDecimal.ZERO;
        BigDecimal parentRate = rule.getParentRebateRate() != null ? rule.getParentRebateRate() : BigDecimal.ZERO;
        BigDecimal grandparentRate = rule.getGrandparentRebateRate() != null ? rule.getGrandparentRebateRate() : BigDecimal.ZERO;
        BigDecimal sum = selfRate.add(parentRate).add(grandparentRate);
        Assert.isTrue(sum.compareTo(BigDecimal.ONE) <= 0, "自购、一级、二级提成比例之和不能大于 100%");
    }

    private CpsSplitRuleDO buildFallbackDefaultRule() {
        return CpsSplitRuleDO.builder()
                .id(0L)
                .ruleName("内置兜底分账规则")
                .platformShareRate(new BigDecimal("0.2000"))
                .selfRebateRate(new BigDecimal("0.6000"))
                .parentRebateRate(new BigDecimal("0.2500"))
                .grandparentRebateRate(new BigDecimal("0.1500"))
                .partnerSpreadRate(BigDecimal.ZERO)
                .attributionPriority(1)
                .autoLockInviterOnFirstOrder(1)
                .freezeDays(15)
                .status(1)
                .priority(0)
                .build();
    }

}

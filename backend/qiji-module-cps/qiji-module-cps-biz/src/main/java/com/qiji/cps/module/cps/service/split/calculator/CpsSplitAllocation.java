package com.qiji.cps.module.cps.service.split.calculator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * CPS 多级分账计算分配结果
 *
 * @author CPS System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CpsSplitAllocation {

    /**
     * 联盟总佣金（分）
     */
    private Long commissionCent;

    /**
     * 平台技术服务留存（分）
     */
    private Long platformShareCent;

    /**
     * 净可分配佣金池（分）= commissionCent - platformShareCent
     */
    private Long distributableCent;

    /**
     * L0 出单人自购返利（分）
     */
    private Long selfRebateCent;

    /**
     * L1 一级直接推荐人提成（分）
     */
    private Long parentRebateCent;

    /**
     * L2 二级间接推荐人提成（分）
     */
    private Long grandparentRebateCent;

    /**
     * 合伙人/团长级差提成预留（分）
     */
    private Long partnerSpreadCent;

    /**
     * 缺位或无资格沉淀至平台的佣金（分）
     */
    private Long unallocatedToPlatformCent;

    /**
     * 平台最终总收益（分）= platformShareCent + unallocatedToPlatformCent
     */
    private Long platformTotalIncomeCent;

    /**
     * 匹配的分账规则ID
     */
    private Long splitRuleId;

    /**
     * 分账规则名称
     */
    private String ruleName;

    /**
     * 平台留存比例快照
     */
    private BigDecimal platformShareRate;

    /**
     * 自购比例快照
     */
    private BigDecimal selfRebateRate;

    /**
     * 一级提成比例快照
     */
    private BigDecimal parentRebateRate;

    /**
     * 二级提成比例快照
     */
    private BigDecimal grandparentRebateRate;

    /**
     * 规则快照 JSON 字符串
     */
    private String ruleSnapshotJson;

}

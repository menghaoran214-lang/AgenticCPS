package com.qiji.cps.module.cps.dal.dataobject.split;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.qiji.cps.framework.tenant.core.db.TenantBaseDO;
import lombok.*;

import java.math.BigDecimal;

/**
 * CPS 多级分账与提成规则配置 DO
 *
 * @author CPS System
 */
@TableName("cps_split_rule")
@KeySequence("cps_split_rule_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CpsSplitRuleDO extends TenantBaseDO {

    /**
     * 主键ID
     */
    @TableId
    private Long id;
    /**
     * 规则名称
     */
    private String ruleName;
    /**
     * 适用平台（NULL表示全平台通用）
     */
    private String platformCode;
    /**
     * 适用出单人等级（NULL表示通用）
     */
    private Long memberLevelId;
    /**
     * 平台留存比例（如0.2000表示扣留20%技术服务费）
     */
    private BigDecimal platformShareRate;
    /**
     * 自购/出单人返利比例（占净佣金池的比例，如0.6000表示60%）
     */
    private BigDecimal selfRebateRate;
    /**
     * 一级直接推荐人提成比例（占净佣金池的比例，如0.2500表示25%）
     */
    private BigDecimal parentRebateRate;
    /**
     * 二级间接推荐人提成比例（占净佣金池的比例，如0.1500表示15%）
     */
    private BigDecimal grandparentRebateRate;
    /**
     * 合伙人/团长级差提成预留比例
     */
    private BigDecimal partnerSpreadRate;
    /**
     * 跟单冲突归属优先级: 1关系链优先 2转链推广者优先
     */
    private Integer attributionPriority;
    /**
     * 新客首单是否自动锁粉绑定转链推广人: 0否 1是
     */
    private Integer autoLockInviterOnFirstOrder;
    /**
     * 提成分账冻结天数（天）
     */
    private Integer freezeDays;
    /**
     * 状态: 0停用 1启用
     */
    private Integer status;
    /**
     * 优先级（越大越优先）
     */
    private Integer priority;

}

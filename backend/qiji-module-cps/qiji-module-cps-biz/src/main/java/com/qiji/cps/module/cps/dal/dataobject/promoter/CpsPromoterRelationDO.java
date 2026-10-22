package com.qiji.cps.module.cps.dal.dataobject.promoter;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.qiji.cps.framework.tenant.core.db.TenantBaseDO;
import lombok.*;

import java.time.LocalDateTime;

/**
 * CPS 推广员分销关系与团队概况 DO
 *
 * @author CPS System
 */
@TableName("cps_promoter_relation")
@KeySequence("cps_promoter_relation_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CpsPromoterRelationDO extends TenantBaseDO {

    /**
     * 主键ID
     */
    @TableId
    private Long id;
    /**
     * 会员用户ID（对应 member_user.id）
     */
    private Long memberId;
    /**
     * 一级直接推荐人会员ID
     */
    private Long parentMemberId;
    /**
     * 二级间接推荐人会员ID
     */
    private Long grandparentMemberId;
    /**
     * 会员专属邀请码（唯一）
     */
    private String inviteCode;
    /**
     * 分销资格状态: 0未激活/冻结 1正常有效
     */
    private Integer promoterStatus;
    /**
     * 推广员等级: 1普通会员 2VIP推广员 3团长/合伙人
     */
    private Integer promoterLevel;
    /**
     * 绑定上级时间
     */
    private LocalDateTime bindTime;
    /**
     * 一级直推下线总人数
     */
    private Integer teamCountL1;
    /**
     * 二级间推下线总人数
     */
    private Integer teamCountL2;
    /**
     * 团队累计出单量
     */
    private Integer teamOrderCount;
    /**
     * 累计自购返利总额（分）
     */
    private Long totalRebateCent;
    /**
     * 累计推广分账提成总额（分）
     */
    private Long totalSplitCent;

}

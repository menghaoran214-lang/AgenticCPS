package com.qiji.cps.module.cps.controller.admin.promoter.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 推广员关系 Resp VO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CpsAdminPromoterRespVO {

    @Schema(description = "主键ID", example = "1")
    private Long id;

    @Schema(description = "会员ID", example = "100")
    private Long memberId;

    @Schema(description = "会员昵称", example = "张三")
    private String nickname;

    @Schema(description = "会员手机号", example = "13800000000")
    private String mobile;

    @Schema(description = "专属邀请码", example = "CP8866")
    private String inviteCode;

    @Schema(description = "一级直接推荐人会员ID", example = "90")
    private Long parentMemberId;

    @Schema(description = "一级直接推荐人昵称", example = "李四")
    private String parentNickname;

    @Schema(description = "二级间接推荐人会员ID", example = "80")
    private Long grandparentMemberId;

    @Schema(description = "推广员层级: 0普通会员 1初级团长 2中级合伙人 3高级合伙人", example = "1")
    private Integer promoterLevel;

    @Schema(description = "分销状态: 0停用 1正常", example = "1")
    private Integer promoterStatus;

    @Schema(description = "直推团队人数", example = "10")
    private Integer teamCountL1;

    @Schema(description = "间推团队人数", example = "25")
    private Integer teamCountL2;

    @Schema(description = "团队总出单数", example = "58")
    private Integer teamOrderCount;

    @Schema(description = "累计自购返利(分)", example = "3000")
    private Long totalRebateCent;

    @Schema(description = "累计自购返利(元)", example = "30.00")
    private BigDecimal totalRebateYuan;

    @Schema(description = "累计推广提成(分)", example = "12000")
    private Long totalSplitCent;

    @Schema(description = "累计推广提成(元)", example = "120.00")
    private BigDecimal totalSplitYuan;

    @Schema(description = "绑定上级时间")
    private LocalDateTime bindTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}

package com.qiji.cps.module.cps.controller.app.promoter.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Schema(description = "用户 App - CPS 推广中心概览 Resp VO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppCpsPromoterSummaryRespVO {

    @Schema(description = "专属邀请码", example = "CP8866")
    private String inviteCode;

    @Schema(description = "推广员等级: 0普通会员 1初级团长 2中级合伙人 3高级合伙人", example = "1")
    private Integer promoterLevel;

    @Schema(description = "上级推荐人ID", example = "100")
    private Long parentMemberId;

    @Schema(description = "上级昵称", example = "张三")
    private String parentNickname;

    @Schema(description = "上级头像", example = "https://...")
    private String parentAvatar;

    @Schema(description = "累计推广提成收益(分)", example = "25800")
    private Long totalCommissionCent;

    @Schema(description = "累计推广提成收益(元)", example = "258.00")
    private BigDecimal totalCommissionYuan;

    @Schema(description = "团队累计出单数", example = "128")
    private Integer totalOrdersCount;

    @Schema(description = "直推下级人数", example = "12")
    private Long directTeamCount;

    @Schema(description = "间推下级人数", example = "45")
    private Long indirectTeamCount;

    @Schema(description = "团队总人数", example = "57")
    private Long totalTeamCount;
}

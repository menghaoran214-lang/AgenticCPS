package com.qiji.cps.module.cps.controller.app.promoter.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "用户 App - 团队成员 Resp VO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppCpsPromoterTeamMemberRespVO {

    @Schema(description = "会员ID", example = "101")
    private Long memberId;

    @Schema(description = "昵称", example = "李四")
    private String nickname;

    @Schema(description = "头像", example = "https://...")
    private String avatar;

    @Schema(description = "手机号(脱敏)", example = "138****8888")
    private String mobile;

    @Schema(description = "关系层级: 1直推 2间推", example = "1")
    private Integer level;

    @Schema(description = "绑定时间")
    private LocalDateTime bindTime;

    @Schema(description = "团队累计出单数", example = "15")
    private Integer teamOrdersCount;

    @Schema(description = "该成员贡献提成金额(分)", example = "1200")
    private Long contributedCommissionCent;

    @Schema(description = "该成员贡献提成金额(元)", example = "12.00")
    private BigDecimal contributedCommissionYuan;
}

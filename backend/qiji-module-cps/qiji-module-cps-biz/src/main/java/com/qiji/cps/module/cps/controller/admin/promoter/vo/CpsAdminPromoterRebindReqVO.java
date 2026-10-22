package com.qiji.cps.module.cps.controller.admin.promoter.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 推广员人工换绑 Req VO")
@Data
public class CpsAdminPromoterRebindReqVO {

    @Schema(description = "会员ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "会员ID不能为空")
    private Long memberId;

    @Schema(description = "新的一级推荐人会员ID(传null表示解绑为无上级)", example = "105")
    private Long newParentMemberId;

    @Schema(description = "换绑操作原因备注", example = "用户申请手工绑定推广人")
    private String reason;
}

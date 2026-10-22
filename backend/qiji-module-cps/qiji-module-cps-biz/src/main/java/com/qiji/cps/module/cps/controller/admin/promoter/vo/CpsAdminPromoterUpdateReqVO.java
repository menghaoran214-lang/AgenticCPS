package com.qiji.cps.module.cps.controller.admin.promoter.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 推广员层级与状态修改 Req VO")
@Data
public class CpsAdminPromoterUpdateReqVO {

    @Schema(description = "会员ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "会员ID不能为空")
    private Long memberId;

    @Schema(description = "推广员层级: 0普通会员 1初级团长 2中级合伙人 3高级合伙人", example = "1")
    private Integer promoterLevel;

    @Schema(description = "分销状态: 0停用 1正常", example = "1")
    private Integer promoterStatus;
}

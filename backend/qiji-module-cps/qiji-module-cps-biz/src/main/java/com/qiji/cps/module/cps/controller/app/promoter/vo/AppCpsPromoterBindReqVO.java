package com.qiji.cps.module.cps.controller.app.promoter.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "用户 App - 绑定邀请人 Req VO")
@Data
public class AppCpsPromoterBindReqVO {

    @Schema(description = "邀请码", requiredMode = Schema.RequiredMode.REQUIRED, example = "CP8866")
    @NotBlank(message = "邀请码不能为空")
    private String inviteCode;
}

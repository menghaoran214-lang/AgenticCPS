package com.qiji.cps.module.cps.controller.app.withdraw.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "用户 App - 提现申请创建 Request VO")
@Data
public class AppCpsWithdrawCreateReqVO {

    @Schema(description = "绑定的提现账户ID（优先传绑定账户）", example = "101")
    private Long accountId;

    @Min(value = 1, message = "提现金额必须大于0")
    @Max(value = Integer.MAX_VALUE, message = "提现金额超出单笔上限")
    @NotNull(message = "提现金额不能为空")
    private Long amountCent;

    @Pattern(regexp = "alipay|wechat|bank", message = "提现类型仅支持 alipay、wechat 或 bank")
    private String withdrawType;

    @Size(max = 128)
    private String withdrawAccount;

    @Size(max = 64)
    private String withdrawAccountName;

    @Schema(description = "短信验证码（大额提现或敏感出金时必填）", example = "123456")
    private String smsCode;

    @NotBlank(message = "幂等键不能为空")
    @Size(max = 128)
    private String idempotencyKey;

}

package com.qiji.cps.module.cps.controller.app.withdraw.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "用户 App - 提现账户保存 Request VO")
@Data
public class AppCpsWithdrawAccountSaveReqVO {

    @Schema(description = "账户编号（修改时传入）")
    private Long id;

    @Schema(description = "账户类型（alipay:支付宝 wechat:微信 bank:银行卡）", requiredMode = Schema.RequiredMode.REQUIRED, example = "alipay")
    @NotBlank(message = "账户类型不能为空")
    @Pattern(regexp = "alipay|wechat|bank", message = "账户类型仅支持 alipay、wechat 或 bank")
    private String type;

    @Schema(description = "提现账号", requiredMode = Schema.RequiredMode.REQUIRED, example = "13800138000")
    @NotBlank(message = "提现账号不能为空")
    @Size(max = 128, message = "提现账号不能超过128位")
    private String account;

    @Schema(description = "真实姓名", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三")
    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 64, message = "真实姓名不能超过64位")
    private String accountName;

    @Schema(description = "开户行（银行卡时有效）", example = "招商银行")
    @Size(max = 64, message = "开户行不能超过64位")
    private String bankName;

    @Schema(description = "是否设为默认账户", example = "true")
    private Boolean isDefault;

    @Schema(description = "短信验证码（用于高安全绑定）", example = "123456")
    private String smsCode;

}

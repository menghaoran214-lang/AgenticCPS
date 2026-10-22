package com.qiji.cps.module.cps.controller.app.withdraw.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Schema(description = "用户 App - 提现账户 Response VO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppCpsWithdrawAccountRespVO {

    @Schema(description = "账户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Long id;

    @Schema(description = "账户类型（alipay:支付宝 wechat:微信 bank:银行卡）", requiredMode = Schema.RequiredMode.REQUIRED, example = "alipay")
    private String type;

    @Schema(description = "提现账号", requiredMode = Schema.RequiredMode.REQUIRED, example = "13800138000")
    private String account;

    @Schema(description = "脱敏提现账号", example = "138****8000")
    private String accountMasked;

    @Schema(description = "真实姓名", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三")
    private String accountName;

    @Schema(description = "脱敏真实姓名", example = "*三")
    private String accountNameMasked;

    @Schema(description = "开户行", example = "招商银行")
    private String bankName;

    @Schema(description = "是否默认账户", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean isDefault;

    @Schema(description = "最近使用时间")
    private LocalDateTime lastUsedTime;

}

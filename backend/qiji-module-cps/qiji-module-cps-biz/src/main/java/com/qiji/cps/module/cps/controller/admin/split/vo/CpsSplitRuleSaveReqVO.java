package com.qiji.cps.module.cps.controller.admin.split.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - CPS 分账规则保存 Req VO")
@Data
public class CpsSplitRuleSaveReqVO {

    @Schema(description = "规则编号", example = "1")
    private Long id;

    @Schema(description = "规则名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "淘宝默认80-50-30-20规则")
    @NotBlank(message = "规则名称不能为空")
    private String ruleName;

    @Schema(description = "适用电商平台(NULL表示全平台)", example = "taobao")
    private String platformCode;

    @Schema(description = "适用会员等级(NULL表示全等级)", example = "1")
    private Long memberLevelId;

    @Schema(description = "平台技术服务费留存比例(0.00-1.00)", requiredMode = Schema.RequiredMode.REQUIRED, example = "0.2000")
    @NotNull(message = "平台留存比例不能为空")
    @DecimalMin(value = "0.0000", message = "平台留存比例不能小于0")
    @DecimalMax(value = "1.0000", message = "平台留存比例不能大于1")
    private BigDecimal platformShareRate;

    @Schema(description = "自购返利比例(0.00-1.00)", requiredMode = Schema.RequiredMode.REQUIRED, example = "0.5000")
    @NotNull(message = "自购返利比例不能为空")
    @DecimalMin(value = "0.0000", message = "自购返利比例不能小于0")
    @DecimalMax(value = "1.0000", message = "自购返利比例不能大于1")
    private BigDecimal selfRebateRate;

    @Schema(description = "一级直接推荐人提成比例(0.00-1.00)", requiredMode = Schema.RequiredMode.REQUIRED, example = "0.3000")
    @NotNull(message = "一级推荐人提成比例不能为空")
    @DecimalMin(value = "0.0000", message = "一级推荐人提成比例不能小于0")
    @DecimalMax(value = "1.0000", message = "一级推荐人提成比例不能大于1")
    private BigDecimal parentRebateRate;

    @Schema(description = "二级间接推荐人提成比例(0.00-1.00)", requiredMode = Schema.RequiredMode.REQUIRED, example = "0.2000")
    @NotNull(message = "二级推荐人提成比例不能为空")
    @DecimalMin(value = "0.0000", message = "二级推荐人提成比例不能小于0")
    @DecimalMax(value = "1.0000", message = "二级推荐人提成比例不能大于1")
    private BigDecimal grandparentRebateRate;

    @Schema(description = "合伙人/团队级差预留比例", example = "0.0000")
    private BigDecimal partnerSpreadRate;

    @Schema(description = "归属冲突优先级: 1关系链优先 2转链推广者优先", example = "2")
    private Integer attributionPriority;

    @Schema(description = "无上级新客首单是否自动锁粉: 0否 1是", example = "1")
    private Integer autoLockInviterOnFirstOrder;

    @Schema(description = "提成分账冻结天数(天)", example = "15")
    private Integer freezeDays;

    @Schema(description = "状态: 0停用 1启用", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "优先级(越大越优先)", example = "10")
    private Integer priority;
}

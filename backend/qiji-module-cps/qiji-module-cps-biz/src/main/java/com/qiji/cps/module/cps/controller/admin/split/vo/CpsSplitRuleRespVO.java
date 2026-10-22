package com.qiji.cps.module.cps.controller.admin.split.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - CPS 分账规则 Resp VO")
@Data
public class CpsSplitRuleRespVO {

    @Schema(description = "规则编号", example = "1")
    private Long id;

    @Schema(description = "规则名称", example = "淘宝默认80-50-30-20规则")
    private String ruleName;

    @Schema(description = "适用电商平台", example = "taobao")
    private String platformCode;

    @Schema(description = "适用会员等级", example = "1")
    private Long memberLevelId;

    @Schema(description = "平台留存技术服务费比例", example = "0.2000")
    private BigDecimal platformShareRate;

    @Schema(description = "自购返利比例", example = "0.5000")
    private BigDecimal selfRebateRate;

    @Schema(description = "一级推荐人提成比例", example = "0.3000")
    private BigDecimal parentRebateRate;

    @Schema(description = "二级推荐人提成比例", example = "0.2000")
    private BigDecimal grandparentRebateRate;

    @Schema(description = "合伙人团队级差比例", example = "0.0000")
    private BigDecimal partnerSpreadRate;

    @Schema(description = "归属冲突优先级: 1关系链优先 2转链推广者优先", example = "2")
    private Integer attributionPriority;

    @Schema(description = "无上级新客首单是否自动锁粉: 0否 1是", example = "1")
    private Integer autoLockInviterOnFirstOrder;

    @Schema(description = "提成分账冻结天数(天)", example = "15")
    private Integer freezeDays;

    @Schema(description = "状态: 0停用 1启用", example = "1")
    private Integer status;

    @Schema(description = "优先级", example = "10")
    private Integer priority;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}

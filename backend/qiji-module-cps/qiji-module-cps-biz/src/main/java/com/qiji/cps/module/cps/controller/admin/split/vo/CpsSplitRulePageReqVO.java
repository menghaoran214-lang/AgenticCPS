package com.qiji.cps.module.cps.controller.admin.split.vo;

import com.qiji.cps.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - CPS 分账规则分页 Req VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CpsSplitRulePageReqVO extends PageParam {

    @Schema(description = "规则名称(模糊)", example = "淘宝")
    private String ruleName;

    @Schema(description = "电商平台", example = "taobao")
    private String platformCode;

    @Schema(description = "状态: 0停用 1启用", example = "1")
    private Integer status;
}

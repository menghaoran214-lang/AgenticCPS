package com.qiji.cps.module.cps.controller.admin.publish.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - CPS投放执行 Response VO")
@Data
public class CpsPublishDispatchRespVO {

    @Schema(description = "投放任务 ID")
    private Long taskId;

    @Schema(description = "投放任务编码")
    private String taskCode;

    @Schema(description = "成功数")
    private Integer successCount;

    @Schema(description = "失败数")
    private Integer failedCount;

    @Schema(description = "跳过数")
    private Integer skippedCount;

    @Schema(description = "执行说明")
    private String message;

}

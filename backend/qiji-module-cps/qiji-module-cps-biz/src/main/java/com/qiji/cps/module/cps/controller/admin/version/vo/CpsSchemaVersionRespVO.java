package com.qiji.cps.module.cps.controller.admin.version.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 数据库结构版本与更新时间 Response VO")
@Data
public class CpsSchemaVersionRespVO {

    @Schema(description = "主键ID", example = "1")
    private Long id;

    @Schema(description = "模块标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "cps")
    private String module;

    @Schema(description = "结构版本号", requiredMode = Schema.RequiredMode.REQUIRED, example = "v2.1.0")
    private String version;

    @Schema(description = "数据库结构版本更新时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime schemaUpdateTime;

    @Schema(description = "本环境执行应用时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime appliedTime;

    @Schema(description = "来源脚本文件", example = "cps-all-in-one.sql")
    private String scriptSource;

    @Schema(description = "版本描述与主要改动说明")
    private String description;

    @Schema(description = "状态", example = "SUCCESS")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}

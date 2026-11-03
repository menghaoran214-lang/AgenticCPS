package com.qiji.cps.module.cps.controller.admin.publish.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Schema(description = "管理后台 - CPS投放任务 Response VO")
@Data
public class CpsPublishTaskRespVO {

    @Schema(description = "任务 ID")
    private Long id;

    @Schema(description = "任务编码")
    private String taskCode;

    @Schema(description = "任务名称")
    private String taskName;

    @Schema(description = "投放内容来源")
    private String sourceType;

    @Schema(description = "投放内容来源主键")
    private Long sourceId;

    @Schema(description = "触达渠道")
    private String channelCode;

    @Schema(description = "目标人群类型")
    private String targetType;

    @Schema(description = "指定会员 ID")
    private String targetIds;

    @Schema(description = "消息模板")
    private String contentTemplate;

    @Schema(description = "推广链接模板")
    private String linkTemplate;

    @Schema(description = "渠道模板编码")
    private String templateCode;

    @Schema(description = "Cron 表达式")
    private String cronExpression;

    @Schema(description = "投放开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @Schema(description = "投放结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @Schema(description = "每个会员每日最大投放次数")
    private Integer frequencyLimitPerDay;

    @Schema(description = "免打扰开始时间")
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime quietStartTime;

    @Schema(description = "免打扰结束时间")
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime quietEndTime;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "上次投放时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastDispatchTime;

    @Schema(description = "下次投放时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime nextDispatchTime;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

}

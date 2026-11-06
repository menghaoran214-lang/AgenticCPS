package com.qiji.cps.module.cps.controller.admin.publish.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Schema(description = "管理后台 - CPS投放任务新增/修改 Request VO")
@Data
public class CpsPublishTaskSaveReqVO {

    @Schema(description = "任务 ID，新增时为空")
    private Long id;

    @Schema(description = "任务编码，留空自动生成")
    private String taskCode;

    @Schema(description = "任务名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "任务名称不能为空")
    private String taskName;

    @Schema(description = "投放内容来源：MANUAL 手工 / SELECTION_THEME 选品主题 / REBATE_ACTIVITY 活动中心 / CPX_TASK 投放任务",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "投放内容来源不能为空")
    private String sourceType;

    @Schema(description = "投放内容来源主键")
    private Long sourceId;

    @Schema(description = "触达渠道：SMS 短信 / NOTIFY 站内信 / CLIPBOARD 文案看板",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "触达渠道不能为空")
    private String channelCode;

    @Schema(description = "目标人群类型：SPECIFIED 指定会员 / REBATE_MEMBER 返利会员",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "目标人群类型不能为空")
    private String targetType;

    @Schema(description = "指定会员 ID，逗号分隔，target_type 为 SPECIFIED 时必填")
    private String targetIds;

    @Schema(description = "消息模板，占位符 {member_id}、{task_name}、{url}",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "消息模板不能为空")
    private String contentTemplate;

    @Schema(description = "推广链接，必须是完整 HTTP(S) 地址，不接受 {member_id} 等归因占位符")
    private String linkTemplate;

    @Schema(description = "渠道模板编码，短信 / 站内信模板使用")
    private String templateCode;

    @Schema(description = "Cron 表达式，Spring 格式（秒 分 时 日 月 周），留空表示只手工触发")
    private String cronExpression;

    @Schema(description = "投放开始时间")
    private LocalDateTime startTime;

    @Schema(description = "投放结束时间")
    private LocalDateTime endTime;

    @Schema(description = "每个会员每日最大投放次数，0 表示不限制")
    @Min(value = 0, message = "每日投放次数不能为负数")
    private Integer frequencyLimitPerDay;

    @Schema(description = "免打扰开始时间")
    private LocalTime quietStartTime;

    @Schema(description = "免打扰结束时间")
    private LocalTime quietEndTime;

    @Schema(description = "备注")
    private String remark;

}

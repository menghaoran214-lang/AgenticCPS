package com.qiji.cps.module.cps.controller.admin.publish.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CPS投放记录 Response VO")
@Data
public class CpsPublishLogRespVO {

    @Schema(description = "记录 ID")
    private Long id;

    @Schema(description = "投放任务 ID")
    private Long taskId;

    @Schema(description = "投放任务编码")
    private String taskCode;

    @Schema(description = "触达渠道")
    private String channelCode;

    @Schema(description = "目标会员 ID")
    private Long memberId;

    @Schema(description = "发送内容快照")
    private String contentSnapshot;

    @Schema(description = "推广链接")
    private String linkUrl;

    @Schema(description = "投放结果")
    private String status;

    @Schema(description = "失败或跳过原因")
    private String errorMsg;

    @Schema(description = "投放时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime sentAt;

}

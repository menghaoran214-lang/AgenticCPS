package com.qiji.cps.module.cps.controller.admin.publish.vo;

import com.qiji.cps.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CPS投放记录分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CpsPublishLogPageReqVO extends PageParam {

    @Schema(description = "投放任务 ID")
    private Long taskId;

    @Schema(description = "投放任务编码")
    private String taskCode;

    @Schema(description = "触达渠道")
    private String channelCode;

    @Schema(description = "目标会员 ID")
    private Long memberId;

    @Schema(description = "投放结果：SUCCESS / FAILED / SKIPPED")
    private String status;

    @Schema(description = "投放开始时间")
    private LocalDateTime startTime;

    @Schema(description = "投放结束时间")
    private LocalDateTime endTime;

}

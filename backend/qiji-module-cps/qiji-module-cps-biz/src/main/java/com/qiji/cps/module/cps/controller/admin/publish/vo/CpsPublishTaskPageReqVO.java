package com.qiji.cps.module.cps.controller.admin.publish.vo;

import com.qiji.cps.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - CPS投放任务分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CpsPublishTaskPageReqVO extends PageParam {

    @Schema(description = "任务编码")
    private String taskCode;

    @Schema(description = "任务名称")
    private String taskName;

    @Schema(description = "触达渠道")
    private String channelCode;

    @Schema(description = "投放内容来源")
    private String sourceType;

    @Schema(description = "目标人群类型")
    private String targetType;

    @Schema(description = "状态")
    private String status;

}

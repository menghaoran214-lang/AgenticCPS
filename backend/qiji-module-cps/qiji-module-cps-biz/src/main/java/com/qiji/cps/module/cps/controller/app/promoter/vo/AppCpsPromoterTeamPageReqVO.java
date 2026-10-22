package com.qiji.cps.module.cps.controller.app.promoter.vo;

import com.qiji.cps.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "用户 App - 推广团队分页 Req VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AppCpsPromoterTeamPageReqVO extends PageParam {

    @Schema(description = "团队层级: 1直推 2间推，空表示全部", example = "1")
    private Integer level;
}

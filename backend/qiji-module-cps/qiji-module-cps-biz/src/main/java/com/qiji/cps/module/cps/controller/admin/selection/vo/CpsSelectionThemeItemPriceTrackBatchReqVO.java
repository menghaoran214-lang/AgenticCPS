package com.qiji.cps.module.cps.controller.admin.selection.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "管理后台 - CPS选品主题商品价格跟踪批量开关 Request VO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CpsSelectionThemeItemPriceTrackBatchReqVO {

    @Schema(description = "商品快照ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "商品快照ID列表不能为空")
    private List<Long> ids;

    @Schema(description = "价格跟踪状态：0关闭 1开启", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "价格跟踪状态不能为空")
    private Integer priceTrackStatus;
}

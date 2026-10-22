package com.qiji.cps.module.cps.controller.admin.selection.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Schema(description = "管理后台 - CPS选品主题商品价格跟踪开关 Request VO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CpsSelectionThemeItemPriceTrackReqVO {

    @Schema(description = "商品快照ID", example = "1024")
    private Long id;

    @Schema(description = "平台编码", example = "taobao")
    private String platformCode;

    @Schema(description = "商品ID", example = "654321")
    private String goodsId;

    @Schema(description = "供应商编码", example = "dataoke")
    private String vendorCode;

    @Schema(description = "商品标题")
    private String title;

    @Schema(description = "商品主图")
    private String mainPic;

    @Schema(description = "券后价（元）")
    private BigDecimal actualPrice;

    @Schema(description = "原价（元）")
    private BigDecimal originalPrice;

    @Schema(description = "优惠券（元）")
    private BigDecimal couponPrice;

    @Schema(description = "价格跟踪状态：0关闭 1开启", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "价格跟踪状态不能为空")
    private Integer priceTrackStatus;
}

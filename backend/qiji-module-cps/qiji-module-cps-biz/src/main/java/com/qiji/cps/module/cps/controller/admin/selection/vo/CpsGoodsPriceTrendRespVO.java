package com.qiji.cps.module.cps.controller.admin.selection.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - CPS商品价格趋势分析 Response VO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CpsGoodsPriceTrendRespVO {

    @Schema(description = "价格跟踪状态：0未开启 1已开启", example = "1")
    private Integer trackStatus;

    @Schema(description = "选品库商品快照ID", example = "1024")
    private Long selectionItemId;

    @Schema(description = "商品ID", example = "654321")
    private String goodsId;

    @Schema(description = "平台编码", example = "taobao")
    private String platformCode;

    @Schema(description = "供应商编码", example = "dataoke")
    private String vendorCode;

    @Schema(description = "商品标题")
    private String title;

    @Schema(description = "当前券后价（元）", example = "29.90")
    private BigDecimal currentPrice;

    @Schema(description = "当前原价（元）", example = "59.90")
    private BigDecimal originalPrice;

    @Schema(description = "当前优惠券（元）", example = "30.00")
    private BigDecimal couponPrice;

    @Schema(description = "历史最低券后价（元）", example = "24.90")
    private BigDecimal minPrice;

    @Schema(description = "历史最高券后价（元）", example = "39.90")
    private BigDecimal maxPrice;

    @Schema(description = "历史平均券后价（元）", example = "31.50")
    private BigDecimal avgPrice;

    @Schema(description = "价格变动金额（元，最新相较最初；负为降价，正为涨价）", example = "-5.00")
    private BigDecimal priceChange;

    @Schema(description = "最近一次价格跟踪抓取时间")
    private LocalDateTime lastTrackTime;

    @Schema(description = "价格趋势走势数据序列")
    private List<CpsGoodsPricePointVO> historyList;
}

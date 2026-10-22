package com.qiji.cps.module.cps.controller.admin.selection.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - CPS商品价格走势数据点 Response VO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CpsGoodsPricePointVO {

    @Schema(description = "快照ID", example = "100")
    private Long id;

    @Schema(description = "快照时间")
    private LocalDateTime snapshotTime;

    @Schema(description = "券后价（元）", example = "29.90")
    private BigDecimal actualPrice;

    @Schema(description = "原价（元）", example = "59.90")
    private BigDecimal originalPrice;

    @Schema(description = "优惠券金额（元）", example = "30.00")
    private BigDecimal couponPrice;

    @Schema(description = "佣金率（%）", example = "20.50")
    private BigDecimal commissionRate;

    @Schema(description = "预估佣金（元）", example = "6.13")
    private BigDecimal commissionAmount;

    @Schema(description = "近30天销量", example = "15000")
    private Long monthSales;

    @Schema(description = "店铺名称", example = "旗舰店")
    private String shopName;

    @Schema(description = "活动标签", example = "聚划算")
    private String activityTag;
}

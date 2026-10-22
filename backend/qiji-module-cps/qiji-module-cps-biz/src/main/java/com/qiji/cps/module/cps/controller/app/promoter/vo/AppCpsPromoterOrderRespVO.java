package com.qiji.cps.module.cps.controller.app.promoter.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "用户 App - 推广订单明细 Resp VO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppCpsPromoterOrderRespVO {

    @Schema(description = "返利/提成记录ID", example = "1001")
    private Long id;

    @Schema(description = "订单ID", example = "5001")
    private Long orderId;

    @Schema(description = "电商平台编码", example = "taobao")
    private String platformCode;

    @Schema(description = "平台订单号", example = "TB123456789")
    private String platformOrderId;

    @Schema(description = "商品ID", example = "689123")
    private String itemId;

    @Schema(description = "商品标题", example = "夏季速干透气T恤")
    private String itemTitle;

    @Schema(description = "订单金额(元)", example = "99.00")
    private BigDecimal orderAmount;

    @Schema(description = "总佣金金额(元)", example = "20.00")
    private BigDecimal commissionAmount;

    @Schema(description = "分账提成比例", example = "25.00")
    private BigDecimal rebateRate;

    @Schema(description = "提成分润金额(元)", example = "4.00")
    private BigDecimal rebateAmount;

    @Schema(description = "提成分润金额(分)", example = "400")
    private Long rebateAmountCent;

    @Schema(description = "分账角色: self自购 parent_l1一级提成 grandparent_l2二级提成", example = "parent_l1")
    private String splitRole;

    @Schema(description = "出单购买人会员ID", example = "108")
    private Long sourceMemberId;

    @Schema(description = "出单购买人昵称", example = "王小二")
    private String sourceMemberNickname;

    @Schema(description = "状态: pending待结算/received已到账/refunded已扣回", example = "received")
    private String rebateStatus;

    @Schema(description = "创建时间/出单时间")
    private LocalDateTime createTime;
}

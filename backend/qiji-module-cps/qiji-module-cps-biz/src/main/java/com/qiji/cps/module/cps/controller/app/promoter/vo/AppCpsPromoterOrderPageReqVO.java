package com.qiji.cps.module.cps.controller.app.promoter.vo;

import com.qiji.cps.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "用户 App - 推广订单明细分页 Req VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AppCpsPromoterOrderPageReqVO extends PageParam {

    @Schema(description = "分账角色过滤: self自购 parent_l1一级提成 grandparent_l2二级提成，空表示全部", example = "parent_l1")
    private String splitRole;
}

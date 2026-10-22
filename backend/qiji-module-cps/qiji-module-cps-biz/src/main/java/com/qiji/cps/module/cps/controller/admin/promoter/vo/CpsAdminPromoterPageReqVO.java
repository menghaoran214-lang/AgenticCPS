package com.qiji.cps.module.cps.controller.admin.promoter.vo;

import com.qiji.cps.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 推广员关系分页 Req VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CpsAdminPromoterPageReqVO extends PageParam {

    @Schema(description = "会员编号", example = "100")
    private Long memberId;

    @Schema(description = "邀请码", example = "CP8866")
    private String inviteCode;

    @Schema(description = "推广员层级: 0普通会员 1初级团长 2中级合伙人 3高级合伙人", example = "1")
    private Integer promoterLevel;

    @Schema(description = "分销资格状态: 0停用 1正常", example = "1")
    private Integer promoterStatus;
}

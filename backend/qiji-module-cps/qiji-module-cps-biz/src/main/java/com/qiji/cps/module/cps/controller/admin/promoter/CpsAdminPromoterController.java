package com.qiji.cps.module.cps.controller.admin.promoter;

import com.qiji.cps.framework.common.pojo.CommonResult;
import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.module.cps.controller.admin.promoter.vo.CpsAdminPromoterPageReqVO;
import com.qiji.cps.module.cps.controller.admin.promoter.vo.CpsAdminPromoterRebindReqVO;
import com.qiji.cps.module.cps.controller.admin.promoter.vo.CpsAdminPromoterRespVO;
import com.qiji.cps.module.cps.controller.admin.promoter.vo.CpsAdminPromoterUpdateReqVO;
import com.qiji.cps.module.cps.dal.dataobject.promoter.CpsPromoterRelationDO;
import com.qiji.cps.module.cps.service.promoter.CpsPromoterRelationService;
import com.qiji.cps.module.cps.service.rebate.asset.CpsMoneyConverter;
import com.qiji.cps.module.member.api.user.MemberUserApi;
import com.qiji.cps.module.member.api.user.dto.MemberUserRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static com.qiji.cps.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - CPS 推广员与分销关系管理
 *
 * @author CPS System
 */
@Tag(name = "管理后台 - CPS 推广员与分销关系")
@RestController
@RequestMapping("/cps/promoter")
@Validated
public class CpsAdminPromoterController {

    @Resource
    private CpsPromoterRelationService promoterRelationService;

    @Resource
    private MemberUserApi memberUserApi;

    @Resource
    private CpsMoneyConverter moneyConverter;

    @GetMapping("/page")
    @Operation(summary = "获得推广员关系分页列表")
    @PreAuthorize("@ss.hasPermission('cps:promoter:query')")
    public CommonResult<PageResult<CpsAdminPromoterRespVO>> getPromoterPage(@Valid CpsAdminPromoterPageReqVO pageReqVO) {
        PageResult<CpsPromoterRelationDO> pageResult = promoterRelationService.getAdminPromoterPage(pageReqVO);
        if (pageResult.getList() == null || pageResult.getList().isEmpty()) {
            return success(new PageResult<>(Collections.emptyList(), pageResult.getTotal()));
        }

        Set<Long> userIds = new HashSet<>();
        pageResult.getList().forEach(rel -> {
            if (rel.getMemberId() != null) userIds.add(rel.getMemberId());
            if (rel.getParentMemberId() != null) userIds.add(rel.getParentMemberId());
        });
        Map<Long, MemberUserRespDTO> userMap = memberUserApi.getUserMap(userIds);

        List<CpsAdminPromoterRespVO> list = pageResult.getList().stream().map(rel -> {
            MemberUserRespDTO selfUser = userMap.get(rel.getMemberId());
            MemberUserRespDTO parentUser = rel.getParentMemberId() != null ? userMap.get(rel.getParentMemberId()) : null;

            Long totalRebateCent = rel.getTotalRebateCent() != null ? rel.getTotalRebateCent() : 0L;
            Long totalSplitCent = rel.getTotalSplitCent() != null ? rel.getTotalSplitCent() : 0L;

            return CpsAdminPromoterRespVO.builder()
                    .id(rel.getId())
                    .memberId(rel.getMemberId())
                    .nickname(selfUser != null ? selfUser.getNickname() : null)
                    .mobile(selfUser != null ? selfUser.getMobile() : null)
                    .inviteCode(rel.getInviteCode())
                    .parentMemberId(rel.getParentMemberId())
                    .parentNickname(parentUser != null ? parentUser.getNickname() : null)
                    .grandparentMemberId(rel.getGrandparentMemberId())
                    .promoterLevel(rel.getPromoterLevel())
                    .promoterStatus(rel.getPromoterStatus())
                    .teamCountL1(rel.getTeamCountL1())
                    .teamCountL2(rel.getTeamCountL2())
                    .teamOrderCount(rel.getTeamOrderCount())
                    .totalRebateCent(totalRebateCent)
                    .totalRebateYuan(moneyConverter.centToYuan(totalRebateCent))
                    .totalSplitCent(totalSplitCent)
                    .totalSplitYuan(moneyConverter.centToYuan(totalSplitCent))
                    .bindTime(rel.getBindTime())
                    .createTime(rel.getCreateTime())
                    .build();
        }).collect(Collectors.toList());

        return success(new PageResult<>(list, pageResult.getTotal()));
    }

    @PutMapping("/rebind")
    @Operation(summary = "管理员人工换绑/解绑上级推荐人")
    @PreAuthorize("@ss.hasPermission('cps:promoter:update')")
    public CommonResult<Boolean> rebindInviter(@Valid @RequestBody CpsAdminPromoterRebindReqVO reqVO) {
        boolean ok = promoterRelationService.rebindInviterAdmin(reqVO.getMemberId(), reqVO.getNewParentMemberId(), reqVO.getReason());
        return success(ok);
    }

    @PutMapping("/update-status")
    @Operation(summary = "修改推广员等级与分销资格状态")
    @PreAuthorize("@ss.hasPermission('cps:promoter:update')")
    public CommonResult<Boolean> updatePromoterStatus(@Valid @RequestBody CpsAdminPromoterUpdateReqVO reqVO) {
        promoterRelationService.updatePromoterLevelAndStatus(reqVO.getMemberId(), reqVO.getPromoterLevel(), reqVO.getPromoterStatus());
        return success(true);
    }
}

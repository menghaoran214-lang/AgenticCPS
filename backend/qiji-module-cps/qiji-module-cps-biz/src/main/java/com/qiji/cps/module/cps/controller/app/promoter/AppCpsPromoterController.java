package com.qiji.cps.module.cps.controller.app.promoter;

import com.qiji.cps.framework.common.pojo.CommonResult;
import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.framework.security.core.util.SecurityFrameworkUtils;
import com.qiji.cps.module.cps.controller.app.promoter.vo.*;
import com.qiji.cps.module.cps.dal.dataobject.promoter.CpsPromoterRelationDO;
import com.qiji.cps.module.cps.dal.dataobject.rebate.CpsRebateRecordDO;
import com.qiji.cps.module.cps.dal.mysql.rebate.CpsRebateRecordMapper;
import com.qiji.cps.module.cps.service.promoter.CpsPromoterRelationService;
import com.qiji.cps.module.cps.service.rebate.asset.CpsMoneyConverter;
import com.qiji.cps.module.member.api.user.MemberUserApi;
import com.qiji.cps.module.member.api.user.dto.MemberUserRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static com.qiji.cps.framework.common.pojo.CommonResult.success;

/**
 * 用户 APP - CPS 分销推广与团队中心
 *
 * @author CPS System
 */
@Tag(name = "用户 APP - CPS 推广中心")
@RestController
@RequestMapping("/cps/promoter")
@Validated
public class AppCpsPromoterController {

    @Resource
    private CpsPromoterRelationService promoterRelationService;

    @Resource
    private CpsRebateRecordMapper rebateRecordMapper;

    @Resource
    private CpsMoneyConverter moneyConverter;

    @Resource
    private MemberUserApi memberUserApi;

    @GetMapping("/summary")
    @Operation(summary = "获得我的推广中心概览（邀请码、收益、团队人数）")
    public CommonResult<AppCpsPromoterSummaryRespVO> getPromoterSummary() {
        Long memberId = SecurityFrameworkUtils.getLoginUserId();
        CpsPromoterRelationDO relation = promoterRelationService.getOrCreateRelation(memberId);

        MemberUserRespDTO parentUser = null;
        if (relation.getParentMemberId() != null) {
            parentUser = memberUserApi.getUser(relation.getParentMemberId());
        }

        long directCount = relation.getTeamCountL1() != null ? relation.getTeamCountL1().longValue() : 0L;
        long indirectCount = relation.getTeamCountL2() != null ? relation.getTeamCountL2().longValue() : 0L;
        long totalCommissionCent = relation.getTotalSplitCent() != null ? relation.getTotalSplitCent() : 0L;
        BigDecimal totalCommissionYuan = moneyConverter.centToYuan(totalCommissionCent);

        AppCpsPromoterSummaryRespVO vo = AppCpsPromoterSummaryRespVO.builder()
                .inviteCode(relation.getInviteCode())
                .promoterLevel(relation.getPromoterLevel())
                .parentMemberId(relation.getParentMemberId())
                .parentNickname(parentUser != null ? parentUser.getNickname() : null)
                .parentAvatar(parentUser != null ? parentUser.getAvatar() : null)
                .totalCommissionCent(totalCommissionCent)
                .totalCommissionYuan(totalCommissionYuan != null ? totalCommissionYuan : BigDecimal.ZERO)
                .totalOrdersCount(relation.getTeamOrderCount() != null ? relation.getTeamOrderCount() : 0)
                .directTeamCount(directCount)
                .indirectTeamCount(indirectCount)
                .totalTeamCount(directCount + indirectCount)
                .build();
        return success(vo);
    }

    @PostMapping("/bind")
    @Operation(summary = "绑定上级推荐人")
    public CommonResult<Boolean> bindInviter(@Valid @RequestBody AppCpsPromoterBindReqVO reqVO) {
        Long memberId = SecurityFrameworkUtils.getLoginUserId();
        boolean success = promoterRelationService.bindInviter(memberId, reqVO.getInviteCode());
        return success(success);
    }

    @GetMapping("/team/page")
    @Operation(summary = "分页查询我的推广团队成员")
    public CommonResult<PageResult<AppCpsPromoterTeamMemberRespVO>> getTeamPage(@Valid AppCpsPromoterTeamPageReqVO reqVO) {
        Long memberId = SecurityFrameworkUtils.getLoginUserId();
        PageResult<CpsPromoterRelationDO> pageResult = promoterRelationService.getTeamPage(memberId, reqVO.getLevel(), reqVO);

        if (pageResult.getList() == null || pageResult.getList().isEmpty()) {
            return success(new PageResult<>(Collections.emptyList(), pageResult.getTotal()));
        }

        Set<Long> teamMemberIds = pageResult.getList().stream()
                .map(CpsPromoterRelationDO::getMemberId)
                .collect(Collectors.toSet());
        Map<Long, MemberUserRespDTO> userMap = memberUserApi.getUserMap(teamMemberIds);

        List<AppCpsPromoterTeamMemberRespVO> list = pageResult.getList().stream().map(rel -> {
            MemberUserRespDTO user = userMap.get(rel.getMemberId());
            int level = memberId.equals(rel.getParentMemberId()) ? 1 : 2;
            String mobileMasked = null;
            if (user != null && user.getMobile() != null && user.getMobile().length() >= 7) {
                mobileMasked = user.getMobile().substring(0, 3) + "****" + user.getMobile().substring(user.getMobile().length() - 4);
            }
            return AppCpsPromoterTeamMemberRespVO.builder()
                    .memberId(rel.getMemberId())
                    .nickname(user != null ? user.getNickname() : "会员" + rel.getMemberId())
                    .avatar(user != null ? user.getAvatar() : null)
                    .mobile(mobileMasked)
                    .level(level)
                    .bindTime(rel.getBindTime())
                    .teamOrdersCount(rel.getTeamOrderCount())
                    .contributedCommissionCent(rel.getTotalRebateCent())
                    .contributedCommissionYuan(moneyConverter.centToYuan(rel.getTotalRebateCent() != null ? rel.getTotalRebateCent() : 0L))
                    .build();
        }).collect(Collectors.toList());

        return success(new PageResult<>(list, pageResult.getTotal()));
    }

    @GetMapping("/team-orders/page")
    @Operation(summary = "分页查询推广提成与出单记录")
    public CommonResult<PageResult<AppCpsPromoterOrderRespVO>> getTeamOrdersPage(@Valid AppCpsPromoterOrderPageReqVO reqVO) {
        Long memberId = SecurityFrameworkUtils.getLoginUserId();
        PageResult<CpsRebateRecordDO> pageResult = rebateRecordMapper.selectPageByMemberAndSplitRole(
                memberId, reqVO.getSplitRole(), reqVO);

        if (pageResult.getList() == null || pageResult.getList().isEmpty()) {
            return success(new PageResult<>(Collections.emptyList(), pageResult.getTotal()));
        }

        Set<Long> sourceMemberIds = pageResult.getList().stream()
                .map(CpsRebateRecordDO::getSourceMemberId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, MemberUserRespDTO> userMap = memberUserApi.getUserMap(sourceMemberIds);

        List<AppCpsPromoterOrderRespVO> list = pageResult.getList().stream().map(rec -> {
            MemberUserRespDTO sourceUser = rec.getSourceMemberId() != null ? userMap.get(rec.getSourceMemberId()) : null;
            return AppCpsPromoterOrderRespVO.builder()
                    .id(rec.getId())
                    .orderId(rec.getOrderId())
                    .platformCode(rec.getPlatformCode())
                    .platformOrderId(rec.getPlatformOrderId())
                    .itemId(rec.getItemId())
                    .itemTitle(rec.getItemTitle())
                    .orderAmount(rec.getOrderAmount())
                    .commissionAmount(rec.getCommissionAmount())
                    .rebateRate(rec.getRebateRate())
                    .rebateAmount(rec.getRebateAmount())
                    .rebateAmountCent(rec.getRebateAmountCent())
                    .splitRole(rec.getSplitRole())
                    .sourceMemberId(rec.getSourceMemberId())
                    .sourceMemberNickname(sourceUser != null ? sourceUser.getNickname() : null)
                    .rebateStatus(rec.getRebateStatus())
                    .createTime(rec.getCreateTime())
                    .build();
        }).collect(Collectors.toList());

        return success(new PageResult<>(list, pageResult.getTotal()));
    }
}

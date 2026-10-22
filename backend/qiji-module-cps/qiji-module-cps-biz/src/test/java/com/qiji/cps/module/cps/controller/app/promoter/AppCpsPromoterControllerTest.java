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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AppCpsPromoterControllerTest {

    @InjectMocks
    private AppCpsPromoterController controller;

    @Mock
    private CpsPromoterRelationService promoterRelationService;

    @Mock
    private CpsRebateRecordMapper rebateRecordMapper;

    @Mock
    private CpsMoneyConverter moneyConverter;

    @Mock
    private MemberUserApi memberUserApi;

    @Test
    @DisplayName("测试推广中心概览查询")
    void testGetPromoterSummary() {
        Long loginUserId = 100L;
        Long parentId = 99L;

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(loginUserId);

            CpsPromoterRelationDO relation = CpsPromoterRelationDO.builder()
                    .memberId(loginUserId)
                    .inviteCode("CP8888")
                    .promoterLevel(1)
                    .parentMemberId(parentId)
                    .totalSplitCent(8800L)
                    .teamOrderCount(25)
                    .teamCountL1(10)
                    .teamCountL2(15)
                    .build();
            when(promoterRelationService.getOrCreateRelation(loginUserId)).thenReturn(relation);

            MemberUserRespDTO parent = new MemberUserRespDTO();
            parent.setId(parentId);
            parent.setNickname("上级老大");
            parent.setAvatar("https://avatar.png");
            when(memberUserApi.getUser(parentId)).thenReturn(parent);

            when(moneyConverter.centToYuan(8800L)).thenReturn(new BigDecimal("88.00"));

            CommonResult<AppCpsPromoterSummaryRespVO> result = controller.getPromoterSummary();
            assertNotNull(result);
            assertEquals(0, result.getCode());

            AppCpsPromoterSummaryRespVO vo = result.getData();
            assertEquals("CP8888", vo.getInviteCode());
            assertEquals("上级老大", vo.getParentNickname());
            assertEquals(25L, vo.getTotalTeamCount());
            assertEquals(new BigDecimal("88.00"), vo.getTotalCommissionYuan());
        }
    }

    @Test
    @DisplayName("测试绑定邀请人")
    void testBindInviter() {
        Long loginUserId = 100L;
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(loginUserId);

            when(promoterRelationService.bindInviter(loginUserId, "CP6666")).thenReturn(true);

            AppCpsPromoterBindReqVO req = new AppCpsPromoterBindReqVO();
            req.setInviteCode("CP6666");

            CommonResult<Boolean> result = controller.bindInviter(req);
            assertTrue(result.getData());
        }
    }

    @Test
    @DisplayName("测试查询我的团队分页")
    void testGetTeamPage() {
        Long loginUserId = 100L;
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(loginUserId);

            CpsPromoterRelationDO child = CpsPromoterRelationDO.builder()
                    .memberId(201L)
                    .parentMemberId(loginUserId)
                    .teamOrderCount(3)
                    .totalRebateCent(500L)
                    .build();
            PageResult<CpsPromoterRelationDO> pageDO = new PageResult<>(List.of(child), 1L);

            AppCpsPromoterTeamPageReqVO req = new AppCpsPromoterTeamPageReqVO();
            req.setLevel(1);
            req.setPageNo(1);
            req.setPageSize(10);

            when(promoterRelationService.getTeamPage(eq(loginUserId), eq(1), any())).thenReturn(pageDO);

            MemberUserRespDTO user201 = new MemberUserRespDTO();
            user201.setId(201L);
            user201.setNickname("小弟1");
            user201.setMobile("13812345678");
            when(memberUserApi.getUserMap(anySet())).thenReturn(Map.of(201L, user201));
            when(moneyConverter.centToYuan(500L)).thenReturn(new BigDecimal("5.00"));

            CommonResult<PageResult<AppCpsPromoterTeamMemberRespVO>> result = controller.getTeamPage(req);
            assertNotNull(result);
            assertEquals(1, result.getData().getList().size());
            assertEquals("小弟1", result.getData().getList().get(0).getNickname());
            assertEquals("138****5678", result.getData().getList().get(0).getMobile());
        }
    }
}

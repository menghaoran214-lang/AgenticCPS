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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CpsAdminPromoterControllerTest {

    @InjectMocks
    private CpsAdminPromoterController controller;

    @Mock
    private CpsPromoterRelationService promoterRelationService;

    @Mock
    private MemberUserApi memberUserApi;

    @Mock
    private CpsMoneyConverter moneyConverter;

    @Test
    @DisplayName("测试管理后台推广员分页列表查询")
    void testGetPromoterPage() {
        CpsAdminPromoterPageReqVO pageReq = new CpsAdminPromoterPageReqVO();
        pageReq.setPageNo(1);
        pageReq.setPageSize(10);

        CpsPromoterRelationDO rel = CpsPromoterRelationDO.builder()
                .id(1L)
                .memberId(100L)
                .parentMemberId(90L)
                .inviteCode("CP1234")
                .promoterLevel(1)
                .promoterStatus(1)
                .teamCountL1(5)
                .teamCountL2(10)
                .totalRebateCent(2000L)
                .totalSplitCent(6000L)
                .build();
        when(promoterRelationService.getAdminPromoterPage(pageReq)).thenReturn(new PageResult<>(List.of(rel), 1L));

        MemberUserRespDTO user100 = new MemberUserRespDTO();
        user100.setId(100L);
        user100.setNickname("推广员小张");
        user100.setMobile("13900000000");

        MemberUserRespDTO user90 = new MemberUserRespDTO();
        user90.setId(90L);
        user90.setNickname("上级老李");

        when(memberUserApi.getUserMap(anySet())).thenReturn(Map.of(100L, user100, 90L, user90));
        when(moneyConverter.centToYuan(2000L)).thenReturn(new BigDecimal("20.00"));
        when(moneyConverter.centToYuan(6000L)).thenReturn(new BigDecimal("60.00"));

        CommonResult<PageResult<CpsAdminPromoterRespVO>> result = controller.getPromoterPage(pageReq);
        assertNotNull(result);
        assertEquals(1, result.getData().getList().size());

        CpsAdminPromoterRespVO vo = result.getData().getList().get(0);
        assertEquals("推广员小张", vo.getNickname());
        assertEquals("上级老李", vo.getParentNickname());
        assertEquals(new BigDecimal("60.00"), vo.getTotalSplitYuan());
    }

    @Test
    @DisplayName("测试管理后台人工换绑与状态更新")
    void testRebindAndUpdateStatus() {
        // 1. Rebind
        CpsAdminPromoterRebindReqVO rebindReq = new CpsAdminPromoterRebindReqVO();
        rebindReq.setMemberId(100L);
        rebindReq.setNewParentMemberId(95L);
        rebindReq.setReason("合规人工修正");

        when(promoterRelationService.rebindInviterAdmin(100L, 95L, "合规人工修正")).thenReturn(true);
        CommonResult<Boolean> rebindResult = controller.rebindInviter(rebindReq);
        assertTrue(rebindResult.getData());

        // 2. Update status
        CpsAdminPromoterUpdateReqVO updateReq = new CpsAdminPromoterUpdateReqVO();
        updateReq.setMemberId(100L);
        updateReq.setPromoterLevel(2);
        updateReq.setPromoterStatus(1);

        CommonResult<Boolean> statusResult = controller.updatePromoterStatus(updateReq);
        assertTrue(statusResult.getData());
        verify(promoterRelationService).updatePromoterLevelAndStatus(100L, 2, 1);
    }
}

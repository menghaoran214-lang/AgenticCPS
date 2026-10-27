package com.qiji.cps.module.cps.service.rebate;

import com.qiji.cps.module.cps.dal.dataobject.freeze.CpsFreezeRecordDO;
import com.qiji.cps.module.cps.dal.dataobject.order.CpsOrderDO;
import com.qiji.cps.module.cps.dal.dataobject.promoter.CpsPromoterRelationDO;
import com.qiji.cps.module.cps.dal.dataobject.rebate.CpsRebateRecordDO;
import com.qiji.cps.module.cps.dal.dataobject.split.CpsSplitRuleDO;
import com.qiji.cps.module.cps.dal.mysql.order.CpsOrderMapper;
import com.qiji.cps.module.cps.dal.mysql.rebate.CpsRebateRecordMapper;
import com.qiji.cps.module.cps.enums.CpsOrderStatusEnum;
import com.qiji.cps.module.cps.enums.CpsRebateStatusEnum;
import com.qiji.cps.module.cps.enums.CpsRebateTypeEnum;
import com.qiji.cps.module.cps.enums.CpsSplitRoleEnum;
import com.qiji.cps.module.cps.service.freeze.CpsFreezeService;
import com.qiji.cps.module.cps.service.promoter.CpsPromoterRelationService;
import com.qiji.cps.module.cps.service.rebate.asset.CpsMoneyConverter;
import com.qiji.cps.module.cps.service.rebate.asset.CpsRebateAssetService;
import com.qiji.cps.module.cps.service.split.CpsSplitRuleService;
import com.qiji.cps.module.cps.service.split.calculator.CpsSplitCalculator;
import com.qiji.cps.module.member.api.user.MemberUserApi;
import com.qiji.cps.module.member.api.user.dto.MemberUserRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CpsRebateSettleMultiLevelTest {

    @Mock
    private CpsOrderMapper orderMapper;
    @Mock
    private CpsRebateRecordMapper rebateRecordMapper;
    @Mock
    private CpsRebateAssetService rebateAssetService;
    @Mock
    private CpsFreezeService freezeService;
    @Mock
    private CpsMoneyConverter moneyConverter;
    @Mock
    private MemberUserApi memberUserApi;
    @Mock
    private CpsPromoterRelationService promoterRelationService;
    @Mock
    private CpsSplitRuleService splitRuleService;

    private CpsSplitCalculator splitCalculator;
    private CpsRebateSettleServiceImpl settleService;

    @BeforeEach
    void setUp() {
        splitCalculator = new CpsSplitCalculator();
        settleService = new CpsRebateSettleServiceImpl();

        ReflectionTestUtils.setField(settleService, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(settleService, "rebateRecordMapper", rebateRecordMapper);
        ReflectionTestUtils.setField(settleService, "rebateAssetService", rebateAssetService);
        ReflectionTestUtils.setField(settleService, "freezeService", freezeService);
        ReflectionTestUtils.setField(settleService, "moneyConverter", moneyConverter);
        ReflectionTestUtils.setField(settleService, "memberUserApi", memberUserApi);
        ReflectionTestUtils.setField(settleService, "promoterRelationService", promoterRelationService);
        ReflectionTestUtils.setField(settleService, "splitRuleService", splitRuleService);
        ReflectionTestUtils.setField(settleService, "splitCalculator", splitCalculator);
    }

    private CpsOrderDO mockOrder(Long orderId, Long memberId, BigDecimal commission) {
        return CpsOrderDO.builder()
                .id(orderId)
                .memberId(memberId)
                .platformCode("taobao")
                .platformOrderId("TB_" + orderId)
                .itemId("ITEM_100")
                .itemTitle("Test Product")
                .finalPrice(new BigDecimal("200.00"))
                .commissionAmount(commission)
                .orderStatus(CpsOrderStatusEnum.SETTLED.getStatus())
                .settleTime(LocalDateTime.now())
                .statusVersion(0)
                .build();
    }

    private CpsSplitRuleDO mockRule() {
        return CpsSplitRuleDO.builder()
                .id(1L)
                .ruleName("Default 80-50-30-20 Rule")
                .platformShareRate(new BigDecimal("0.2000"))
                .selfRebateRate(new BigDecimal("0.5000"))
                .parentRebateRate(new BigDecimal("0.3000"))
                .grandparentRebateRate(new BigDecimal("0.2000"))
                .freezeDays(15)
                .status(1)
                .build();
    }

    @Test
    @DisplayName("完整三级分润结算：自购L0、一级L1、二级L2按80%净佣金池瓜分，生成3条返利并冻结记账")
    void testThreeLevelSettlementSuccess() {
        Long buyerId = 100L;
        Long parentId = 200L;
        Long grandParentId = 300L;
        Long orderId = 1001L;

        CpsOrderDO order = mockOrder(orderId, buyerId, new BigDecimal("100.00"));
        when(orderMapper.selectForUpdateById(orderId)).thenReturn(order);

        MemberUserRespDTO member = new MemberUserRespDTO();
        member.setId(buyerId);
        member.setLevelId(1L);
        when(memberUserApi.getUser(buyerId)).thenReturn(member);

        when(splitRuleService.matchSplitRule("taobao", 1L)).thenReturn(mockRule());
        when(freezeService.getActiveConfig(eq("taobao"), anyLong()))
                .thenReturn(new com.qiji.cps.module.cps.dal.dataobject.freeze.CpsFreezeConfigDO());

        CpsPromoterRelationDO buyerRel = CpsPromoterRelationDO.builder()
                .memberId(buyerId).parentMemberId(parentId).grandparentMemberId(grandParentId)
                .promoterStatus(1).build();
        CpsPromoterRelationDO parentRel = CpsPromoterRelationDO.builder()
                .memberId(parentId).parentMemberId(grandParentId)
                .promoterStatus(1).build();
        CpsPromoterRelationDO grandRel = CpsPromoterRelationDO.builder()
                .memberId(grandParentId)
                .promoterStatus(1).build();

        when(promoterRelationService.getOrCreateRelation(buyerId)).thenReturn(buyerRel);
        when(promoterRelationService.getOrCreateRelation(parentId)).thenReturn(parentRel);
        when(promoterRelationService.getOrCreateRelation(grandParentId)).thenReturn(grandRel);

        when(moneyConverter.yuanToCent(new BigDecimal("100.00"))).thenReturn(10000L);
        when(moneyConverter.centToYuan(4000L)).thenReturn(new BigDecimal("40.00"));
        when(moneyConverter.centToYuan(2400L)).thenReturn(new BigDecimal("24.00"));
        when(moneyConverter.centToYuan(1600L)).thenReturn(new BigDecimal("16.00"));

        when(rebateAssetService.createOrderRebateFreeze(eq(orderId), anyString(), eq(15)))
                .thenReturn(CpsFreezeRecordDO.builder().id(501L).unfreezeTime(LocalDateTime.now().plusDays(15)).build());
        when(rebateAssetService.createSplitRebateFreeze(eq(parentId), eq(orderId), anyString(), anyString(), anyString(), eq(2400L), eq(15), anyString()))
                .thenReturn(CpsFreezeRecordDO.builder().id(502L).build());
        when(rebateAssetService.createSplitRebateFreeze(eq(grandParentId), eq(orderId), anyString(), anyString(), anyString(), eq(1600L), eq(15), anyString()))
                .thenReturn(CpsFreezeRecordDO.builder().id(503L).build());

        when(orderMapper.updateRebateFreezeByStatusVersion(any(CpsOrderDO.class), eq(0))).thenReturn(1);

        boolean settled = settleService.settleOrder(order);
        assertTrue(settled);

        // 校验出单人、一级、二级返利记录分别插入
        ArgumentCaptor<CpsRebateRecordDO> recordCaptor = ArgumentCaptor.forClass(CpsRebateRecordDO.class);
        verify(rebateRecordMapper, times(3)).insert(recordCaptor.capture());

        var records = recordCaptor.getAllValues();
        assertEquals(3, records.size());

        CpsRebateRecordDO selfRecord = records.stream()
                .filter(r -> CpsSplitRoleEnum.SELF.getRole().equals(r.getSplitRole())).findFirst().orElse(null);
        assertNotNull(selfRecord);
        assertEquals(buyerId, selfRecord.getMemberId());
        assertEquals(4000L, selfRecord.getRebateAmountCent());

        CpsRebateRecordDO l1Record = records.stream()
                .filter(r -> CpsSplitRoleEnum.PARENT_L1.getRole().equals(r.getSplitRole())).findFirst().orElse(null);
        assertNotNull(l1Record);
        assertEquals(parentId, l1Record.getMemberId());
        assertEquals(2400L, l1Record.getRebateAmountCent());

        CpsRebateRecordDO l2Record = records.stream()
                .filter(r -> CpsSplitRoleEnum.GRANDPARENT_L2.getRole().equals(r.getSplitRole())).findFirst().orElse(null);
        assertNotNull(l2Record);
        assertEquals(grandParentId, l2Record.getMemberId());
        assertEquals(1600L, l2Record.getRebateAmountCent());

        // 校验团队业绩统计累计
        verify(promoterRelationService).recordTeamStats(buyerId, 4000L, 2400L, 1600L);
    }

    @Test
    @DisplayName("无上级推荐人时：仅自购L0得返利，一级与二级提成沉淀至平台留存，不插入L1/L2记录")
    void testNoInviterOnlySelfRebate() {
        Long buyerId = 100L;
        Long orderId = 1002L;

        CpsOrderDO order = mockOrder(orderId, buyerId, new BigDecimal("100.00"));
        when(orderMapper.selectForUpdateById(orderId)).thenReturn(order);

        MemberUserRespDTO member = new MemberUserRespDTO();
        member.setId(buyerId);
        member.setLevelId(1L);
        when(memberUserApi.getUser(buyerId)).thenReturn(member);

        when(splitRuleService.matchSplitRule("taobao", 1L)).thenReturn(mockRule());
        when(freezeService.getActiveConfig(eq("taobao"), anyLong()))
                .thenReturn(new com.qiji.cps.module.cps.dal.dataobject.freeze.CpsFreezeConfigDO());

        CpsPromoterRelationDO buyerRel = CpsPromoterRelationDO.builder()
                .memberId(buyerId).parentMemberId(null).grandparentMemberId(null)
                .promoterStatus(1).build();
        when(promoterRelationService.getOrCreateRelation(buyerId)).thenReturn(buyerRel);

        when(moneyConverter.yuanToCent(new BigDecimal("100.00"))).thenReturn(10000L);
        when(moneyConverter.centToYuan(4000L)).thenReturn(new BigDecimal("40.00"));

        when(rebateAssetService.createOrderRebateFreeze(eq(orderId), anyString(), eq(15)))
                .thenReturn(CpsFreezeRecordDO.builder().id(501L).unfreezeTime(LocalDateTime.now().plusDays(15)).build());
        when(orderMapper.updateRebateFreezeByStatusVersion(any(CpsOrderDO.class), eq(0))).thenReturn(1);

        boolean settled = settleService.settleOrder(order);
        assertTrue(settled);

        // 仅插入自购记录
        ArgumentCaptor<CpsRebateRecordDO> recordCaptor = ArgumentCaptor.forClass(CpsRebateRecordDO.class);
        verify(rebateRecordMapper, times(1)).insert(recordCaptor.capture());
        assertEquals(CpsSplitRoleEnum.SELF.getRole(), recordCaptor.getValue().getSplitRole());

        // 资产服务不为L1/L2冻结
        verify(rebateAssetService, never()).createSplitRebateFreeze(any(), any(), any(), any(), any(), anyLong(), anyInt(), any());
        verify(promoterRelationService).recordTeamStats(buyerId, 4000L, 0L, 0L);
    }

    @Test
    @DisplayName("直接入账完成后保留已到账状态，不使用旧版本再次回写订单")
    void testDirectCreditPreservesReceivedOrderAndCreditsStation() {
        Long orderId = 1003L;
        Long buyerId = 100L;
        Long parentId = 200L;
        CpsOrderDO order = mockOrder(orderId, buyerId, new BigDecimal("100.00"));
        when(orderMapper.selectForUpdateById(orderId)).thenReturn(order);
        MemberUserRespDTO member = new MemberUserRespDTO();
        member.setId(buyerId);
        member.setLevelId(1L);
        when(memberUserApi.getUser(buyerId)).thenReturn(member);
        when(splitRuleService.matchSplitRule("taobao", 1L)).thenReturn(mockRule());
        when(promoterRelationService.getOrCreateRelation(buyerId)).thenReturn(
                CpsPromoterRelationDO.builder().memberId(buyerId).parentMemberId(parentId)
                        .promoterStatus(1).build());
        when(promoterRelationService.getOrCreateRelation(parentId)).thenReturn(
                CpsPromoterRelationDO.builder().memberId(parentId).promoterStatus(1).build());
        when(moneyConverter.yuanToCent(new BigDecimal("100.00"))).thenReturn(10000L);
        when(moneyConverter.centToYuan(4000L)).thenReturn(new BigDecimal("40.00"));
        when(moneyConverter.centToYuan(2400L)).thenReturn(new BigDecimal("24.00"));

        // The real asset service atomically advances the database row to REBATE_RECEIVED/version 1.
        // The settlement snapshot still holds version 0; another CAS must fail and roll back.
        CpsOrderDO persisted = mockOrder(orderId, buyerId, new BigDecimal("100.00"));
        when(rebateAssetService.creditOrderRebate(orderId, "order-rebate-credit:" + orderId))
                .thenAnswer(invocation -> {
                    persisted.setOrderStatus(CpsOrderStatusEnum.REBATE_RECEIVED.getStatus());
                    persisted.setStatusVersion(1);
                    persisted.setRealRebate(new BigDecimal("40.00"));
                    return null;
                });

        assertTrue(settleService.settleOrder(order));
        assertEquals(CpsOrderStatusEnum.REBATE_RECEIVED.getStatus(), persisted.getOrderStatus());
        assertEquals(1, persisted.getStatusVersion());
        assertEquals(new BigDecimal("40.00"), persisted.getRealRebate());
        verify(orderMapper, never()).updateRebateFreezeByStatusVersion(any(), any());
        verify(rebateAssetService).creditSplitRebate(eq(parentId), eq(orderId), eq("TB_" + orderId),
                eq("ORDER_SPLIT_CREDIT"), eq("l1:" + orderId), eq(2400L), anyString());
        verify(rebateAssetService, never()).createOrderRebateFreeze(any(), anyString(), anyInt());
        verify(promoterRelationService).recordTeamStats(buyerId, 4000L, 2400L, 0L);
    }
}

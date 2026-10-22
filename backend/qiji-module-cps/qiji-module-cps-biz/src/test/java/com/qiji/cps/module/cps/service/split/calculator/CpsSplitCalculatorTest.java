package com.qiji.cps.module.cps.service.split.calculator;

import com.qiji.cps.module.cps.dal.dataobject.promoter.CpsPromoterRelationDO;
import com.qiji.cps.module.cps.dal.dataobject.split.CpsSplitRuleDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CPS 多级分账纯计算引擎单元测试")
public class CpsSplitCalculatorTest {

    private final CpsSplitCalculator calculator = new CpsSplitCalculator();

    private CpsSplitRuleDO createStandardRule() {
        return CpsSplitRuleDO.builder()
                .id(100L)
                .ruleName("标准三级分账规则")
                .platformShareRate(new BigDecimal("0.2000"))
                .selfRebateRate(new BigDecimal("0.6000"))
                .parentRebateRate(new BigDecimal("0.2500"))
                .grandparentRebateRate(new BigDecimal("0.1500"))
                .freezeDays(15)
                .build();
    }

    @Test
    @DisplayName("标准三级完整链条分账测试 (100元总佣金)")
    public void testStandardThreeTierSplit() {
        // 总佣金 10000 分 = 100.00 元
        long commissionCent = 10000L;
        CpsSplitRuleDO rule = createStandardRule();

        // A (二级 101) -> B (一级 102) -> C (出单人 103)
        CpsPromoterRelationDO buyer = CpsPromoterRelationDO.builder()
                .memberId(103L).parentMemberId(102L).grandparentMemberId(101L).promoterStatus(1).build();
        CpsPromoterRelationDO parent = CpsPromoterRelationDO.builder()
                .memberId(102L).promoterStatus(1).build();
        CpsPromoterRelationDO grandparent = CpsPromoterRelationDO.builder()
                .memberId(101L).promoterStatus(1).build();

        CpsSplitAllocation allocation = calculator.calculateSplit(commissionCent, rule, buyer, parent, grandparent);

        assertNotNull(allocation);
        assertEquals(10000L, allocation.getCommissionCent());
        // 平台留存 20%: 2000 分
        assertEquals(2000L, allocation.getPlatformShareCent());
        // 净佣金池 80%: 8000 分
        assertEquals(8000L, allocation.getDistributableCent());
        // 自购 60% of 8000 = 4800 分
        assertEquals(4800L, allocation.getSelfRebateCent());
        // 一级 25% of 8000 = 2000 分
        assertEquals(2000L, allocation.getParentRebateCent());
        // 二级 15% of 8000 = 1200 分
        assertEquals(1200L, allocation.getGrandparentRebateCent());
        // 未沉淀: 0 分
        assertEquals(0L, allocation.getUnallocatedToPlatformCent());
        // 平台总收益 = 2000 分
        assertEquals(2000L, allocation.getPlatformTotalIncomeCent());

        // 资金守恒检查
        assertEquals(commissionCent,
                allocation.getSelfRebateCent() + allocation.getParentRebateCent()
                        + allocation.getGrandparentRebateCent() + allocation.getPlatformTotalIncomeCent());
    }

    @Test
    @DisplayName("缺少二级上线上行链路测试 (未分配金额沉淀至平台)")
    public void testIncompleteChainWithoutGrandparent() {
        long commissionCent = 10000L;
        CpsSplitRuleDO rule = createStandardRule();

        // 仅有一级 B (102)，无二级
        CpsPromoterRelationDO buyer = CpsPromoterRelationDO.builder()
                .memberId(103L).parentMemberId(102L).grandparentMemberId(null).promoterStatus(1).build();
        CpsPromoterRelationDO parent = CpsPromoterRelationDO.builder()
                .memberId(102L).promoterStatus(1).build();

        CpsSplitAllocation allocation = calculator.calculateSplit(commissionCent, rule, buyer, parent, null);

        assertEquals(4800L, allocation.getSelfRebateCent());
        assertEquals(2000L, allocation.getParentRebateCent());
        assertEquals(0L, allocation.getGrandparentRebateCent()); // 无二级
        assertEquals(1200L, allocation.getUnallocatedToPlatformCent()); // 二级 1200 沉淀至平台
        assertEquals(3200L, allocation.getPlatformTotalIncomeCent()); // 2000 基础 + 1200 沉淀

        assertEquals(commissionCent,
                allocation.getSelfRebateCent() + allocation.getParentRebateCent()
                        + allocation.getGrandparentRebateCent() + allocation.getPlatformTotalIncomeCent());
    }

    @Test
    @DisplayName("散客无任何上级测试 (一级与二级提成全部沉淀至平台)")
    public void testSolitaryBuyerWithoutAnyParent() {
        long commissionCent = 10000L;
        CpsSplitRuleDO rule = createStandardRule();

        CpsPromoterRelationDO buyer = CpsPromoterRelationDO.builder()
                .memberId(103L).parentMemberId(null).grandparentMemberId(null).promoterStatus(1).build();

        CpsSplitAllocation allocation = calculator.calculateSplit(commissionCent, rule, buyer, null, null);

        assertEquals(4800L, allocation.getSelfRebateCent());
        assertEquals(0L, allocation.getParentRebateCent());
        assertEquals(0L, allocation.getGrandparentRebateCent());
        assertEquals(3200L, allocation.getUnallocatedToPlatformCent()); // 2000 + 1200 全部沉淀
        assertEquals(5200L, allocation.getPlatformTotalIncomeCent()); // 2000 + 3200

        assertEquals(commissionCent,
                allocation.getSelfRebateCent() + allocation.getParentRebateCent()
                        + allocation.getGrandparentRebateCent() + allocation.getPlatformTotalIncomeCent());
    }

    @Test
    @DisplayName("上级分销资格被冻结测试 (冻结推广员不发放提成，沉淀至平台)")
    public void testFrozenPromoterQualification() {
        long commissionCent = 10000L;
        CpsSplitRuleDO rule = createStandardRule();

        CpsPromoterRelationDO buyer = CpsPromoterRelationDO.builder()
                .memberId(103L).parentMemberId(102L).grandparentMemberId(101L).promoterStatus(1).build();
        // 一级正常
        CpsPromoterRelationDO parent = CpsPromoterRelationDO.builder()
                .memberId(102L).promoterStatus(1).build();
        // 二级被冻结 status = 0
        CpsPromoterRelationDO grandparent = CpsPromoterRelationDO.builder()
                .memberId(101L).promoterStatus(0).build();

        CpsSplitAllocation allocation = calculator.calculateSplit(commissionCent, rule, buyer, parent, grandparent);

        assertEquals(4800L, allocation.getSelfRebateCent());
        assertEquals(2000L, allocation.getParentRebateCent());
        assertEquals(0L, allocation.getGrandparentRebateCent()); // 冻结故为 0
        assertEquals(1200L, allocation.getUnallocatedToPlatformCent());
        assertEquals(3200L, allocation.getPlatformTotalIncomeCent());

        assertEquals(commissionCent,
                allocation.getSelfRebateCent() + allocation.getParentRebateCent()
                        + allocation.getGrandparentRebateCent() + allocation.getPlatformTotalIncomeCent());
    }

    @Test
    @DisplayName("零佣金或负数测试")
    public void testZeroOrNegativeCommission() {
        CpsSplitRuleDO rule = createStandardRule();
        CpsSplitAllocation zero = calculator.calculateSplit(0L, rule, null, null, null);
        assertEquals(0L, zero.getCommissionCent());
        assertEquals(0L, zero.getSelfRebateCent());
        assertEquals(0L, zero.getPlatformTotalIncomeCent());

        CpsSplitAllocation negative = calculator.calculateSplit(-100L, rule, null, null, null);
        assertEquals(-100L, negative.getCommissionCent());
        assertEquals(0L, negative.getSelfRebateCent());
    }

    @Test
    @DisplayName("奇数小额分账精度与舍入保护测试 (7分钱、99分钱)")
    public void testOddAmountsAndRoundingPrecision() {
        CpsSplitRuleDO rule = createStandardRule();
        CpsPromoterRelationDO buyer = CpsPromoterRelationDO.builder()
                .memberId(103L).parentMemberId(102L).grandparentMemberId(101L).promoterStatus(1).build();
        CpsPromoterRelationDO parent = CpsPromoterRelationDO.builder()
                .memberId(102L).promoterStatus(1).build();
        CpsPromoterRelationDO grandparent = CpsPromoterRelationDO.builder()
                .memberId(101L).promoterStatus(1).build();

        for (long cent : new long[]{1L, 2L, 3L, 7L, 99L, 101L, 9999L}) {
            CpsSplitAllocation allocation = calculator.calculateSplit(cent, rule, buyer, parent, grandparent);
            // 验证绝不超分
            assertTrue(allocation.getSelfRebateCent() + allocation.getParentRebateCent()
                    + allocation.getGrandparentRebateCent() <= allocation.getDistributableCent());
            // 验证守恒
            assertEquals(cent,
                    allocation.getSelfRebateCent() + allocation.getParentRebateCent()
                            + allocation.getGrandparentRebateCent() + allocation.getPlatformTotalIncomeCent(),
                    "金额 " + cent + " 分资金守恒失败");
        }
    }

    @Test
    @DisplayName("规则配置比例超出100%时触发断言拦截")
    public void testOverAllocationRuleThrowsException() {
        CpsSplitRuleDO illegalRule = CpsSplitRuleDO.builder()
                .platformShareRate(new BigDecimal("0.2000"))
                .selfRebateRate(new BigDecimal("0.6000"))
                .parentRebateRate(new BigDecimal("0.3000"))
                .grandparentRebateRate(new BigDecimal("0.2000")) // 0.6 + 0.3 + 0.2 = 1.1 > 1.0!
                .build();

        assertThrows(IllegalArgumentException.class, () ->
                calculator.calculateSplit(1000L, illegalRule, null, null, null));
    }

}

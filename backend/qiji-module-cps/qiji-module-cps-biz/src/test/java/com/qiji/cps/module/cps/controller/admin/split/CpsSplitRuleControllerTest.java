package com.qiji.cps.module.cps.controller.admin.split;

import com.qiji.cps.framework.common.pojo.CommonResult;
import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.module.cps.controller.admin.split.vo.CpsSplitRulePageReqVO;
import com.qiji.cps.module.cps.controller.admin.split.vo.CpsSplitRuleRespVO;
import com.qiji.cps.module.cps.controller.admin.split.vo.CpsSplitRuleSaveReqVO;
import com.qiji.cps.module.cps.dal.dataobject.split.CpsSplitRuleDO;
import com.qiji.cps.module.cps.service.split.CpsSplitRuleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CpsSplitRuleControllerTest {

    @InjectMocks
    private CpsSplitRuleController controller;

    @Mock
    private CpsSplitRuleService splitRuleService;

    @Test
    @DisplayName("测试分账规则增删改查控制器")
    void testCrudOperations() {
        // 1. Create
        CpsSplitRuleSaveReqVO saveReq = new CpsSplitRuleSaveReqVO();
        saveReq.setRuleName("测试规则");
        saveReq.setPlatformCode("taobao");
        saveReq.setPlatformShareRate(new BigDecimal("0.2000"));
        saveReq.setSelfRebateRate(new BigDecimal("0.5000"));
        saveReq.setParentRebateRate(new BigDecimal("0.3000"));
        saveReq.setGrandparentRebateRate(new BigDecimal("0.2000"));
        saveReq.setStatus(1);

        when(splitRuleService.saveSplitRule(any(CpsSplitRuleDO.class))).thenReturn(101L);
        CommonResult<Long> createResult = controller.createSplitRule(saveReq);
        assertEquals(101L, createResult.getData());

        // 2. Update
        saveReq.setId(101L);
        CommonResult<Boolean> updateResult = controller.updateSplitRule(saveReq);
        assertTrue(updateResult.getData());
        verify(splitRuleService, times(2)).saveSplitRule(any(CpsSplitRuleDO.class));

        // 3. Get
        CpsSplitRuleDO ruleDO = CpsSplitRuleDO.builder()
                .id(101L)
                .ruleName("测试规则")
                .platformCode("taobao")
                .platformShareRate(new BigDecimal("0.2000"))
                .selfRebateRate(new BigDecimal("0.5000"))
                .parentRebateRate(new BigDecimal("0.3000"))
                .grandparentRebateRate(new BigDecimal("0.2000"))
                .status(1)
                .build();
        when(splitRuleService.getSplitRule(101L)).thenReturn(ruleDO);
        CommonResult<CpsSplitRuleRespVO> getResult = controller.getSplitRule(101L);
        assertEquals("测试规则", getResult.getData().getRuleName());

        // 4. Page
        CpsSplitRulePageReqVO pageReq = new CpsSplitRulePageReqVO();
        pageReq.setPageNo(1);
        pageReq.setPageSize(10);
        when(splitRuleService.getSplitRulePage(pageReq)).thenReturn(new PageResult<>(List.of(ruleDO), 1L));
        CommonResult<PageResult<CpsSplitRuleRespVO>> pageResult = controller.getSplitRulePage(pageReq);
        assertEquals(1, pageResult.getData().getList().size());

        // 5. Delete
        CommonResult<Boolean> deleteResult = controller.deleteSplitRule(101L);
        assertTrue(deleteResult.getData());
        verify(splitRuleService).deleteSplitRule(101L);
    }
}

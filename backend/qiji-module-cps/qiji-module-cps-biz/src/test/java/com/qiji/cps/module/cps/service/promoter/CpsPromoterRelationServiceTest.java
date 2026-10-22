package com.qiji.cps.module.cps.service.promoter;

import com.qiji.cps.module.cps.dal.dataobject.promoter.CpsPromoterRelationDO;
import com.qiji.cps.module.cps.dal.mysql.promoter.CpsPromoterRelationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CPS 推广员分销关系 Service 单元测试")
public class CpsPromoterRelationServiceTest {

    @Mock
    private CpsPromoterRelationMapper promoterRelationMapper;

    @InjectMocks
    private CpsPromoterRelationServiceImpl promoterRelationService;

    private CpsPromoterRelationDO userA;
    private CpsPromoterRelationDO userB;
    private CpsPromoterRelationDO userC;

    @BeforeEach
    public void setup() {
        userA = CpsPromoterRelationDO.builder()
                .id(1L).memberId(101L).inviteCode("CODEA").promoterStatus(1).teamCountL1(0).teamCountL2(0).build();
        userB = CpsPromoterRelationDO.builder()
                .id(2L).memberId(102L).inviteCode("CODEB").promoterStatus(1).teamCountL1(0).teamCountL2(0).build();
        userC = CpsPromoterRelationDO.builder()
                .id(3L).memberId(103L).inviteCode("CODEC").promoterStatus(1).teamCountL1(0).teamCountL2(0).build();
    }

    @Test
    @DisplayName("首次访问获取推广关系时自动创建并分配邀请码")
    public void testGetOrCreateRelationNewUser() {
        when(promoterRelationMapper.selectByMemberId(200L)).thenReturn(null);
        when(promoterRelationMapper.insert(any(CpsPromoterRelationDO.class))).thenReturn(1);

        CpsPromoterRelationDO relation = promoterRelationService.getOrCreateRelation(200L);

        assertNotNull(relation);
        assertEquals(200L, relation.getMemberId());
        assertNotNull(relation.getInviteCode());
        assertTrue(relation.getInviteCode().length() >= 6);
        verify(promoterRelationMapper, times(1)).insert(any(CpsPromoterRelationDO.class));
    }

    @Test
    @DisplayName("成功绑定一级与二级推荐人 (A -> B -> C)")
    public void testBindInviterSuccess() {
        // 先设 B 的上级为 A
        userB.setParentMemberId(101L);

        when(promoterRelationMapper.selectByMemberId(103L)).thenReturn(userC);
        when(promoterRelationMapper.selectByMemberId(102L)).thenReturn(userB);

        boolean success = promoterRelationService.bindInviterByParentMemberId(103L, 102L);

        assertTrue(success);
        // 验证 C 的 parent=102, grandparent=101
        verify(promoterRelationMapper, times(1)).updateById(argThat((CpsPromoterRelationDO rel) ->
                Objects.equals(rel.getId(), 3L)
                        && Objects.equals(rel.getParentMemberId(), 102L)
                        && Objects.equals(rel.getGrandparentMemberId(), 101L)));

        // 验证递增 B 的 L1 直推
        verify(promoterRelationMapper, times(1)).incrTeamCountL1(102L, 1);
        // 验证递增 A 的 L2 间推
        verify(promoterRelationMapper, times(1)).incrTeamCountL2(101L, 1);
    }

    @Test
    @DisplayName("严禁绑定自己为推荐人")
    public void testBindSelfRejected() {
        boolean success = promoterRelationService.bindInviterByParentMemberId(101L, 101L);
        assertFalse(success);
        verify(promoterRelationMapper, never()).updateById(any(CpsPromoterRelationDO.class));
    }

    @Test
    @DisplayName("严禁成环循环绑定 (A -> B -> C, C 试图绑定 A)")
    public void testCycleBindingRejected() {
        // A -> B -> C
        userA.setParentMemberId(103L); // 假设 A 已经间接把 C 当作祖先
        userB.setParentMemberId(101L);
        userC.setParentMemberId(null);

        when(promoterRelationMapper.selectByMemberId(103L)).thenReturn(userC);
        when(promoterRelationMapper.selectByMemberId(102L)).thenReturn(userB);
        when(promoterRelationMapper.selectByMemberId(101L)).thenReturn(userA);

        assertThrows(IllegalArgumentException.class, () ->
                promoterRelationService.bindInviterByParentMemberId(103L, 102L));
    }

    @Test
    @DisplayName("已绑定用户重复绑定相同上级时幂等成功，绑定不同上级时拒绝")
    public void testIdempotentBinding() {
        userC.setParentMemberId(102L);
        when(promoterRelationMapper.selectByMemberId(103L)).thenReturn(userC);

        // 重复绑定相同
        boolean same = promoterRelationService.bindInviterByParentMemberId(103L, 102L);
        assertTrue(same);
        verify(promoterRelationMapper, never()).updateById(any(CpsPromoterRelationDO.class));

        // 绑定不同
        boolean different = promoterRelationService.bindInviterByParentMemberId(103L, 999L);
        assertFalse(different);
    }

    @Test
    @DisplayName("团队出单与收益原子累计")
    public void testRecordTeamStats() {
        userC.setParentMemberId(102L);
        userC.setGrandparentMemberId(101L);
        when(promoterRelationMapper.selectByMemberId(103L)).thenReturn(userC);

        promoterRelationService.recordTeamStats(103L, 4800L, 2000L, 1200L);

        // 出单人 C: 增加自购返利 4800
        verify(promoterRelationMapper, times(1)).incrStats(103L, 1, 4800L, 0L);
        // 一级 B: 增加分销提成 2000
        verify(promoterRelationMapper, times(1)).incrStats(102L, 1, 0L, 2000L);
        // 二级 A: 增加分销提成 1200
        verify(promoterRelationMapper, times(1)).incrStats(101L, 1, 0L, 1200L);
    }

}

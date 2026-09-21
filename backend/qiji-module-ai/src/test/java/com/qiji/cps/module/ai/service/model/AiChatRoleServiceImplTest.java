package com.qiji.cps.module.ai.service.model;

import com.qiji.cps.framework.common.enums.CommonStatusEnum;
import com.qiji.cps.framework.test.core.ut.BaseMockitoUnitTest;
import com.qiji.cps.module.ai.dal.dataobject.model.AiChatRoleDO;
import com.qiji.cps.module.ai.dal.mysql.model.AiChatRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.stream.Stream;

import static com.qiji.cps.framework.test.core.util.AssertUtils.assertServiceException;
import static com.qiji.cps.module.ai.enums.ErrorCodeConstants.CHAT_ROLE_MEMBER_DISABLED;
import static com.qiji.cps.module.ai.enums.ErrorCodeConstants.CHAT_ROLE_MEMBER_NOT_AVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class AiChatRoleServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private AiChatRoleServiceImpl chatRoleService;

    @Mock
    private AiChatRoleMapper chatRoleMapper;

    @Test
    void getMemberEnabledChatRoleList_includesPublishedRolesWithSkills() {
        AiChatRoleDO eligible = role(1L, true, true, CommonStatusEnum.ENABLE.getStatus());
        AiChatRoleDO skillRole = role(6L, true, true, CommonStatusEnum.ENABLE.getStatus())
                .setSkillIds(List.of(7L));
        AiChatRoleDO privateRole = role(2L, false, true, CommonStatusEnum.ENABLE.getStatus());
        AiChatRoleDO disabledRole = role(3L, true, true, CommonStatusEnum.DISABLE.getStatus());
        AiChatRoleDO notMemberEnabledRole = role(4L, true, false, CommonStatusEnum.ENABLE.getStatus());
        AiChatRoleDO historicalRole = role(5L, true, null, CommonStatusEnum.ENABLE.getStatus());
        when(chatRoleMapper.selectList(any())).thenReturn(List.of(eligible, skillRole, privateRole, disabledRole,
                notMemberEnabledRole, historicalRole));

        List<AiChatRoleDO> roles = chatRoleService.getMemberEnabledChatRoleList();

        assertEquals(List.of(eligible, skillRole), roles);
    }

    @Test
    void getDefaultMemberChatRole_usesFirstSortedEligibleRole() {
        AiChatRoleDO first = role(1L, true, true, CommonStatusEnum.ENABLE.getStatus());
        AiChatRoleDO second = role(2L, true, true, CommonStatusEnum.ENABLE.getStatus());
        when(chatRoleMapper.selectList(any())).thenReturn(List.of(first, second));

        assertEquals(first, chatRoleService.getDefaultMemberChatRole());
    }

    @Test
    void getDefaultMemberChatRole_rejectsMissingBackendConfiguration() {
        when(chatRoleMapper.selectList(any())).thenReturn(List.of());

        assertServiceException(chatRoleService::getDefaultMemberChatRole, CHAT_ROLE_MEMBER_NOT_AVAILABLE);
    }

    @Test
    void getPublishedChatRoleList_returnsPublishedCustomRolesWhenCapabilityCenterIsOff() {
        AiChatRoleDO custom = role(9L, true, false, CommonStatusEnum.ENABLE.getStatus())
                .setRoleType("CUSTOM").setPublishStatus("PUBLISHED");
        when(chatRoleMapper.selectList(any())).thenReturn(List.of(custom));

        assertEquals(List.of(custom), chatRoleService.getPublishedChatRoleList());
    }

    @ParameterizedTest
    @MethodSource("ineligibleRoles")
    void validateMemberEnabledChatRole_rejectsIneligibleRole(AiChatRoleDO role) {
        when(chatRoleMapper.selectById(eq(role.getId()))).thenReturn(role);

        assertServiceException(() -> chatRoleService.validateMemberEnabledChatRole(role.getId()),
                CHAT_ROLE_MEMBER_DISABLED);
    }

    private static Stream<Arguments> ineligibleRoles() {
        return Stream.of(
                Arguments.of(role(2L, false, true, CommonStatusEnum.ENABLE.getStatus())),
                Arguments.of(role(3L, true, true, CommonStatusEnum.DISABLE.getStatus())),
                Arguments.of(role(4L, true, false, CommonStatusEnum.ENABLE.getStatus())),
                Arguments.of(role(5L, true, null, CommonStatusEnum.ENABLE.getStatus())));
    }

    private static AiChatRoleDO role(Long id, Boolean publicStatus, Boolean memberEnabled, Integer status) {
        return new AiChatRoleDO().setId(id).setName("role-" + id).setPublicStatus(publicStatus)
                .setMemberEnabled(memberEnabled).setStatus(status).setPublishStatus("PUBLISHED");
    }

}

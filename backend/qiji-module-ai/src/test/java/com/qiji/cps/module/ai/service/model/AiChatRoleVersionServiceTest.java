package com.qiji.cps.module.ai.service.model;

import com.qiji.cps.framework.common.util.json.JsonUtils;
import com.qiji.cps.module.ai.controller.admin.model.vo.chatRole.AiChatRoleSaveReqVO;
import com.qiji.cps.module.ai.dal.dataobject.model.AiChatRoleDO;
import com.qiji.cps.module.ai.dal.dataobject.model.AiChatRoleVersionDO;
import com.qiji.cps.module.ai.dal.mysql.model.AiChatRoleMapper;
import com.qiji.cps.module.ai.dal.mysql.model.AiChatRoleVersionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiChatRoleVersionServiceTest {
    private final AiChatRoleMapper roles = mock(AiChatRoleMapper.class);
    private final AiChatRoleVersionMapper versions = mock(AiChatRoleVersionMapper.class);
    private final AiChatRoleServiceImpl service = new AiChatRoleServiceImpl();
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "chatRoleMapper", roles);
        ReflectionTestUtils.setField(service, "roleVersionMapper", versions);
    }
    @Test void creatingRolePersistsFirstDefinitionWithGeneratedIdAndNoInternalMetadata() {
        doAnswer(call -> { ((AiChatRoleDO)call.getArgument(0)).setId(8L); return 1; }).when(roles).insert(any(AiChatRoleDO.class));
        AiChatRoleSaveReqVO request = new AiChatRoleSaveReqVO();
        request.setName("Selector"); request.setSystemMessage("Use verified product evidence");
        request.setId(999L); request.setVersion(100);
        assertEquals(8L, service.createChatRole(request));
        var saved = ArgumentCaptor.forClass(AiChatRoleVersionDO.class);
        verify(versions).insert(saved.capture());
        assertEquals(8L, saved.getValue().getRoleId()); assertEquals(1, saved.getValue().getVersion());
        Map<?,?> snapshot = JsonUtils.parseObject(saved.getValue().getDefinitionSnapshot(), Map.class);
        assertEquals("Use verified product evidence", snapshot.get("systemMessage"));
        assertFalse(snapshot.containsKey("tenantId")); assertFalse(snapshot.containsKey("creator"));
    }
    @Test void staleConcurrentEditorDoesNotWriteRoleOrHistory() {
        when(roles.selectOne(any())).thenReturn(new AiChatRoleDO().setId(8L).setVersion(4));
        AiChatRoleSaveReqVO request = new AiChatRoleSaveReqVO(); request.setId(8L); request.setVersion(3);
        assertThrows(IllegalArgumentException.class, () -> service.updateChatRole(request));
        verify(roles, never()).updateById(any(AiChatRoleDO.class)); verifyNoInteractions(versions);
    }
    @Test void editPreservesUnspecifiedDefinitionAndStoresNewRevisionAfterUpdate() {
        var existing = new AiChatRoleDO().setId(8L).setVersion(4).setName("Old")
                .setSystemMessage("Existing prompt").setRoleType("CUSTOM").setPublishStatus("DRAFT")
                .setRiskLevel("READ_ONLY").setPublicStatus(true);
        when(roles.selectOne(any())).thenReturn(existing);
        AiChatRoleSaveReqVO request = new AiChatRoleSaveReqVO(); request.setId(8L); request.setVersion(4); request.setName("New");
        service.updateChatRole(request);
        var saved = ArgumentCaptor.forClass(AiChatRoleVersionDO.class);
        var order = inOrder(roles, versions);
        order.verify(roles).selectOne(any()); order.verify(roles).updateById(any(AiChatRoleDO.class));
        order.verify(versions).insert(saved.capture());
        assertEquals(5, saved.getValue().getVersion());
        Map<?,?> snapshot = JsonUtils.parseObject(saved.getValue().getDefinitionSnapshot(), Map.class);
        assertEquals("New", snapshot.get("name")); assertEquals("Existing prompt", snapshot.get("systemMessage"));
    }
    @Test void privateHistoryRequiresRoleOwnerBeforeAnyHistoryQuery() {
        when(roles.selectById(8L)).thenReturn(new AiChatRoleDO().setId(8L).setUserId(9L).setPublicStatus(false));
        assertThrows(RuntimeException.class, () -> service.getVersionHistory(8L, 2L));
        verifyNoInteractions(versions);
    }
}

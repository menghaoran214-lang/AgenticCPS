package com.qiji.cps.module.ai.service.chat;

import com.qiji.cps.framework.test.core.ut.BaseMockitoUnitTest;
import com.qiji.cps.framework.tenant.core.context.TenantContextHolder;
import com.qiji.cps.module.ai.dal.dataobject.chat.AiChatConversationDO;
import com.qiji.cps.module.ai.dal.dataobject.model.AiChatRoleDO;
import com.qiji.cps.module.ai.dal.dataobject.model.AiToolDO;
import com.qiji.cps.module.ai.service.model.AiChatRoleService;
import com.qiji.cps.module.ai.service.model.AiToolService;
import com.qiji.cps.module.ai.service.capability.McpRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import com.qiji.cps.module.ai.service.chat.tool.AiChatToolAction;
import com.qiji.cps.module.ai.service.chat.tool.AiChatToolExecutionEvent;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

class AiChatToolCallbackServiceTest extends BaseMockitoUnitTest {

    @InjectMocks
    private AiChatToolCallbackService toolCallbackService;

    @Mock
    private AiChatRoleService chatRoleService;
    @Mock
    private AiToolService toolService;
    @Mock
    private ToolCallbackResolver toolCallbackResolver;
    @Mock
    private McpRegistryService mcpRegistryService;
    @Mock
    private ToolCallback externalCallback;
    @Mock
    private ToolCallback localCallback;
    @BeforeEach
    void setUpMcpRegistry() {
        lenient().when(externalCallback.getToolDefinition()).thenReturn(
                org.springframework.ai.tool.definition.ToolDefinition.builder()
                        .name("external_search").description("external").inputSchema("{}").build());
        lenient().when(localCallback.getToolDefinition()).thenReturn(
                org.springframework.ai.tool.definition.ToolDefinition.builder()
                        .name("local_tool").description("local").inputSchema("{}").build());
        lenient().when(mcpRegistryService.getToolCallbacksByCode("external"))
                .thenReturn(List.of(externalCallback));
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void snapshotMustNotInheritToolsAddedToCurrentRole() {
        when(chatRoleService.getChatRole(10L)).thenReturn(new AiChatRoleDO().setToolIds(List.of(999L)));
        var conversation = new AiChatConversationDO().setRoleId(10L)
                .setEffectiveCapabilitySnapshot("{\"toolIds\":null,\"mcpClientNames\":null}");
        assertTrue(toolCallbackService.getToolCallbacks(conversation).isEmpty());
        verifyNoInteractions(toolService);
    }

    @Test
    void malformedSnapshotMustFailClosed() {
        when(chatRoleService.getChatRole(10L)).thenReturn(new AiChatRoleDO().setToolIds(List.of(999L)));
        var conversation = new AiChatConversationDO().setRoleId(10L).setEffectiveCapabilitySnapshot("invalid");
        assertTrue(toolCallbackService.getToolCallbacks(conversation).isEmpty());
        verifyNoInteractions(toolService);
    }

    @Test
    void getToolCallbacks_mixesLocalCallbacksAndDatabaseManagedMcp() {
        AiChatRoleDO role = new AiChatRoleDO().setToolIds(List.of(1L)).setMcpClientNames(List.of("external"));
        when(chatRoleService.getChatRole(10L)).thenReturn(role);
        when(toolService.getToolList(List.of(1L))).thenReturn(List.of(new AiToolDO().setName("local_tool")));
        when(toolCallbackResolver.resolve("local_tool")).thenReturn(localCallback);

        List<ToolCallback> callbacks = toolCallbackService.getToolCallbacks(
                new AiChatConversationDO().setRoleId(10L).setChatMode("STANDARD"));

        assertEquals(2, callbacks.size());
        assertSame(localCallback, callbacks.get(0));
        assertSame(externalCallback, callbacks.get(1));
        verify(mcpRegistryService).getToolCallbacksByCode("external");
    }

    @Test
    void getToolCallbacks_keepsLocalToolsWhenExternalMcpIsUnavailable() {
        AiChatRoleDO role = new AiChatRoleDO().setToolIds(List.of(1L)).setMcpClientNames(List.of("external"));
        when(chatRoleService.getChatRole(10L)).thenReturn(role);
        when(toolService.getToolList(List.of(1L))).thenReturn(List.of(new AiToolDO().setName("local_tool")));
        when(toolCallbackResolver.resolve("local_tool")).thenReturn(localCallback);
        when(mcpRegistryService.getToolCallbacksByCode("external"))
                .thenThrow(new IllegalStateException("unavailable"));

        List<ToolCallback> callbacks = toolCallbackService.getToolCallbacks(
                new AiChatConversationDO().setRoleId(10L).setChatMode("STANDARD"));

        assertEquals(List.of(localCallback), callbacks);
    }

    @Test
    void getToolCallbacks_selfMcpTestUsesDatabaseManagedConversationServer() {
        AiChatRoleDO role = new AiChatRoleDO().setToolIds(List.of(1L));
        when(chatRoleService.getChatRole(10L)).thenReturn(role);
        AiChatConversationDO conversation = new AiChatConversationDO().setId(99L).setRoleId(10L)
                .setUserId(5L).setMemberId(42L).setOwnerUserType("ADMIN")
                .setChatMode("SELF_MCP_TEST").setMcpClientName("external").setAllowMutation(true);
        TenantContextHolder.setTenantId(7L);
        List<ToolCallback> callbacks = toolCallbackService.getToolCallbacks(conversation);

        assertEquals(1, callbacks.size());
        assertSame(externalCallback, callbacks.get(0));
        verify(mcpRegistryService).getToolCallbacksByCode("external");
        verifyNoInteractions(toolService, toolCallbackResolver);
    }

    @Test
    void getToolCallbacks_wrapsKnownToolAndEmitsLifecycleWithoutChangingToolContext() {
        AiChatRoleDO role = new AiChatRoleDO().setToolIds(List.of(1L));
        when(chatRoleService.getChatRole(10L)).thenReturn(role);
        when(toolService.getToolList(List.of(1L))).thenReturn(List.of(new AiToolDO().setName("cps_search_goods")));
        when(toolCallbackResolver.resolve("cps_search_goods")).thenReturn(localCallback);
        when(localCallback.getToolDefinition()).thenReturn(org.springframework.ai.tool.definition.ToolDefinition.builder()
                .name("cps_search_goods").description("search").inputSchema("{}").build());
        ToolContext context = new ToolContext(Map.of("LOGIN_USER_ID", 42L));
        when(localCallback.call("{}", context)).thenReturn("ok");
        AiChatToolAction action = new AiChatToolAction().setIntent("SEARCH_GOODS")
                .setToolName("cps_search_goods").setLabel("商品搜索")
                .setRunningMessage("正在搜索商品").setSuccessMessage("商品搜索完成");
        List<AiChatToolExecutionEvent> events = new ArrayList<>();

        ToolCallback callback = toolCallbackService.getToolCallbacks(
                new AiChatConversationDO().setRoleId(10L), Map.of("cps_search_goods", action), events::add).get(0);
        assertEquals("ok", callback.call("{}", context));

        verify(localCallback).call("{}", context);
        assertEquals(List.of("TOOL_STARTED", "TOOL_SUCCEEDED"),
                events.stream().map(AiChatToolExecutionEvent::getEventType).toList());
        assertEquals("SEARCH_GOODS", events.get(0).getIntent());
        assertEquals(events.get(0).getExecutionId(), events.get(1).getExecutionId());
    }

    @Test
    void getToolCallbacks_emitsFailedLifecycleEventAndRethrowsToolException() {
        AiChatRoleDO role = new AiChatRoleDO().setToolIds(List.of(1L));
        when(chatRoleService.getChatRole(10L)).thenReturn(role);
        when(toolService.getToolList(List.of(1L))).thenReturn(List.of(new AiToolDO().setName("cps_search_goods")));
        when(toolCallbackResolver.resolve("cps_search_goods")).thenReturn(localCallback);
        when(localCallback.getToolDefinition()).thenReturn(org.springframework.ai.tool.definition.ToolDefinition.builder()
                .name("cps_search_goods").description("search").inputSchema("{}").build());
        IllegalStateException failure = new IllegalStateException("provider unavailable");
        ToolContext context = new ToolContext(Map.of());
        when(localCallback.call("{}", context)).thenThrow(failure);
        AiChatToolAction action = new AiChatToolAction().setIntent("SEARCH_GOODS")
                .setToolName("cps_search_goods").setLabel("商品搜索")
                .setRunningMessage("正在搜索商品").setSuccessMessage("商品搜索完成");
        List<AiChatToolExecutionEvent> events = new ArrayList<>();
        ToolCallback callback = toolCallbackService.getToolCallbacks(
                new AiChatConversationDO().setRoleId(10L), Map.of("cps_search_goods", action), events::add).get(0);

        IllegalStateException thrown = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> callback.call("{}", context));

        assertSame(failure, thrown);
        assertEquals(List.of("TOOL_STARTED", "TOOL_FAILED"),
                events.stream().map(AiChatToolExecutionEvent::getEventType).toList());
        assertEquals(events.get(0).getExecutionId(), events.get(1).getExecutionId());
        assertEquals("FAILED", events.get(1).getStatus());
    }

}

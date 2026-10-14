package com.qiji.cps.module.ai.service.capability;

import com.qiji.cps.framework.common.util.json.JsonUtils;
import com.qiji.cps.framework.tenant.core.context.TenantContextHolder;
import com.qiji.cps.module.ai.dal.dataobject.capability.AiToolConfirmationDO;
import com.qiji.cps.module.ai.dal.dataobject.chat.AiChatConversationDO;
import com.qiji.cps.module.ai.dal.mysql.capability.AiToolConfirmationMapper;
import com.qiji.cps.module.ai.service.chat.AiChatConversationService;
import com.qiji.cps.module.ai.service.chat.AiChatIdentityContextService;
import com.qiji.cps.module.ai.util.AiUtils;
import com.qiji.cps.framework.test.core.ut.BaseMockitoUnitTest;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.test.util.ReflectionTestUtils;
import com.qiji.cps.module.ai.dal.mysql.model.AiToolMapper;
import com.qiji.cps.module.ai.dal.dataobject.model.AiToolDO;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiCapabilityRuntimeTest extends BaseMockitoUnitTest {
    @InjectMocks private AiCapabilityRuntime runtime;
    @Mock private McpRegistryService registry;
    @Mock private ToolCallbackResolver resolver;
    @Mock private AiToolConfirmationMapper confirmations;
    @Mock private AiChatConversationService conversations;
    @Spy private AiChatIdentityContextService identities = new AiChatIdentityContextService();
    @Mock private AiCapabilityAuditService audit;
    @Mock private ToolCallback delegate;
    @Mock private AiToolMapper localTools;
    @Mock private com.qiji.cps.module.ai.dal.mysql.chat.AiChatMessageMapper messages;

    @BeforeEach void tenant() {
        TenantContextHolder.setTenantId(8L);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "test"), AiToolConfirmationDO.class);
    }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test void writeNeverRunsBeforeServerConfirmation() {
        var conversation = conversation("ASSET_WRITE", true, 9L);
        when(resolver.resolve("test_tool")).thenReturn(delegate);
        doAnswer(call -> { call.<AiToolConfirmationDO>getArgument(0).setId(10L); return 1; })
                .when(confirmations).insert(any(AiToolConfirmationDO.class));
        assertTrue(runtime.callbacks(conversation).get(0).call("{}").contains("CONFIRM_REQUIRED"));
        verify(delegate, never()).call(anyString(), any(ToolContext.class));
    }

    @Test void attributionWriteRunsDirectlyWithoutConfirmation() {
        var conversation = conversation("ATTRIBUTION_WRITE", true, 9L);
        when(resolver.resolve("test_tool")).thenReturn(delegate);
        when(delegate.call(anyString(), any(ToolContext.class))).thenAnswer(call -> {
            ToolContext context = call.getArgument(1);
            assertEquals(true, context.getContext().get(AiUtils.TOOL_CONTEXT_ALLOW_MUTATION));
            assertEquals(9L, context.getContext().get(AiUtils.TOOL_CONTEXT_LOGIN_USER_ID));
            return "link_result";
        });
        assertEquals("link_result", runtime.callbacks(conversation).get(0).call("{}"));
        verifyNoInteractions(confirmations);
        verify(delegate, times(1)).call(anyString(), any(ToolContext.class));
    }

    @Test void suppliedIdentityIsIgnoredAndCapturedTenantIsRestored() {
        var conversation = conversation("READ_ONLY", false, 9L);
        when(resolver.resolve("test_tool")).thenReturn(delegate);
        when(delegate.call(anyString(), any(ToolContext.class))).thenAnswer(call -> {
            ToolContext trusted = call.getArgument(1);
            assertTrue(AiChatIdentityContextService.isTrustedLocalToolContext(trusted));
            assertEquals(9L, trusted.getContext().get(AiUtils.TOOL_CONTEXT_LOGIN_USER_ID));
            assertEquals(8L, TenantContextHolder.getTenantId());
            return "ok";
        });
        var callback = runtime.callbacks(conversation).get(0);
        TenantContextHolder.setTenantId(777L);
        assertEquals("ok", callback.call("{}", new ToolContext(Map.of(AiUtils.TOOL_CONTEXT_LOGIN_USER_ID,666L))));
        assertEquals(777L, TenantContextHolder.getTenantId());
    }

    @Test void modelCannotPassIdentityInArguments() {
        when(resolver.resolve("test_tool")).thenReturn(delegate);
        String result = runtime.callbacks(conversation("READ_ONLY",false,9L)).get(0)
                .call("{\"memberId\":666}");
        assertTrue(result.contains("身份"));
        verify(delegate, never()).call(anyString(), any(ToolContext.class));
    }

    @Test void mutationWithoutBoundMemberIsDenied() {
        when(resolver.resolve("test_tool")).thenReturn(delegate);
        assertTrue(runtime.callbacks(conversation("ATTRIBUTION_WRITE",true,null)).get(0).call("{}").contains("会员"));
        verifyNoInteractions(confirmations);
        verify(delegate, never()).call(anyString(), any(ToolContext.class));
    }

    @Test void otherOwnerCannotConsumeConfirmation() {
        when(confirmations.selectById(5L)).thenReturn(new AiToolConfirmationDO().setOwnerId(999L));
        assertThrows(IllegalArgumentException.class, () -> runtime.confirm(5L,42L));
        verifyNoInteractions(conversations,resolver);
    }

    @Test void consumedOrExpiredConfirmationNeverExecutes() {
        var conversation = conversation("ASSET_WRITE",true,9L);
        when(confirmations.selectById(5L)).thenReturn(new AiToolConfirmationDO().setOwnerId(42L)
                .setConversationId(1L).setToolName("test_tool").setArguments("{}"));
        when(conversations.getOwnedConversation(1L,"ADMIN",42L)).thenReturn(conversation);
        when(resolver.resolve("test_tool")).thenReturn(delegate);
        assertThrows(IllegalStateException.class, () -> runtime.confirm(5L,42L));
        verify(delegate, never()).call(anyString(), any(ToolContext.class));
    }

    @Test void confirmedWriteUsesTrustedMutationFlagAndCannotBeReplayed() {
        var conversation = conversation("ASSET_WRITE",true,9L);
        when(confirmations.selectById(5L)).thenReturn(new AiToolConfirmationDO().setId(5L).setOwnerId(42L)
                .setConversationId(1L).setToolName("test_tool").setArguments("{}"));
        when(conversations.getOwnedConversation(1L,"ADMIN",42L)).thenReturn(conversation);
        when(resolver.resolve("test_tool")).thenReturn(delegate);
        when(confirmations.update(isNull(),any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1,1,0);
        when(delegate.call(eq("{}"),any(ToolContext.class))).thenAnswer(call -> {
            ToolContext context = call.getArgument(1);
            assertEquals(true,context.getContext().get(AiUtils.TOOL_CONTEXT_ALLOW_MUTATION));
            assertEquals(9L,context.getContext().get(AiUtils.TOOL_CONTEXT_LOGIN_USER_ID));
            return "done";
        });
        assertEquals("done",runtime.confirm(5L,42L));
        assertThrows(IllegalStateException.class, () -> runtime.confirm(5L,42L));
        verify(delegate,times(1)).call(anyString(),any(ToolContext.class));
    }

    private AiChatConversationDO conversation(String risk, boolean mutation, Long member) {
        lenient().when(localTools.selectOne(any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class),eq("test_tool")))
                .thenReturn(new AiToolDO().setName("test_tool").setStatus(0));
        ReflectionTestUtils.setField(runtime,"riskProviders",List.of((AiCapabilityRiskProvider) name -> risk));
        AiCapabilitySnapshot snapshot = new AiCapabilitySnapshot();
        snapshot.setSchemaVersion(2); snapshot.setRoleCode("test"); snapshot.setRoleVersion(1);
        snapshot.setRiskLevel(risk); snapshot.setAllowMutation(mutation);
        snapshot.getTools().add(new AiCapabilitySnapshot.Tool().setName("test_tool").setRiskLevel(risk));
        return new AiChatConversationDO().setId(1L).setUserId(42L).setOwnerUserType("ADMIN").setMemberId(member)
                .setEffectiveCapabilitySnapshot(JsonUtils.toJsonString(snapshot));
    }
}

package com.qiji.cps.module.ai.service.chat;

import com.qiji.cps.framework.test.core.ut.BaseMockitoUnitTest;
import com.qiji.cps.framework.tenant.core.context.TenantContextHolder;
import com.qiji.cps.module.ai.controller.admin.chat.vo.message.AiChatMessageSendReqVO;
import com.qiji.cps.module.ai.dal.mysql.chat.AiChatMessageMapper;
import com.qiji.cps.module.ai.dal.dataobject.chat.AiChatConversationDO;
import com.qiji.cps.module.ai.service.chat.tool.AiChatToolActionService;
import com.qiji.cps.module.ai.service.chat.tool.AiChatToolAction;
import com.qiji.cps.module.ai.service.chat.tool.AiChatToolRiskLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static com.qiji.cps.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.qiji.cps.module.ai.enums.ErrorCodeConstants.CHAT_CONVERSATION_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class AiChatMessageServiceImplIdentityTest extends BaseMockitoUnitTest {

    @InjectMocks
    private AiChatMessageServiceImpl messageService;

    @Mock
    private AiChatConversationService conversationService;
    @Mock
    private AiChatMessageMapper messageMapper;
    @Mock
    private AiChatToolActionService toolActionService;

    @BeforeEach
    void setTenantContext() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void sendMessage_bindsConversationLookupToAdminOwner() {
        when(conversationService.getOwnedConversation(7L, "ADMIN", 42L))
                .thenThrow(exception(CHAT_CONVERSATION_NOT_EXISTS));

        assertThrows(RuntimeException.class, () -> messageService.sendMessage(
                new AiChatMessageSendReqVO().setConversationId(7L), 42L));

        verify(conversationService).getOwnedConversation(7L, "ADMIN", 42L);
    }

    @Test
    void sendMessageStream_bindsConversationLookupToAdminOwner() {
        when(conversationService.getOwnedConversation(7L, "ADMIN", 42L))
                .thenThrow(exception(CHAT_CONVERSATION_NOT_EXISTS));

        var results = messageService.sendChatMessageStream(
                new AiChatMessageSendReqVO().setConversationId(7L), 42L)
                .collectList().block();

        assertNotNull(results);
        assertEquals(com.qiji.cps.module.ai.enums.ErrorCodeConstants.CHAT_STREAM_ERROR.getCode(),
                results.get(results.size() - 1).getCode());
        verify(conversationService).getOwnedConversation(7L, "ADMIN", 42L);
    }

    @Test
    void sendMessage_bindsConversationLookupToMemberOwner() {
        when(conversationService.getOwnedConversation(7L, "MEMBER", 99L))
                .thenThrow(exception(CHAT_CONVERSATION_NOT_EXISTS));

        assertThrows(RuntimeException.class, () -> messageService.sendMessage(
                new AiChatMessageSendReqVO().setConversationId(7L), "MEMBER", 99L));

        verify(conversationService).getOwnedConversation(7L, "MEMBER", 99L);
    }

    @Test
    void sendMessageStream_bindsConversationLookupToMemberOwner() {
        when(conversationService.getOwnedConversation(7L, "MEMBER", 99L))
                .thenThrow(exception(CHAT_CONVERSATION_NOT_EXISTS));

        var results = messageService.sendChatMessageStream(
                new AiChatMessageSendReqVO().setConversationId(7L), "MEMBER", 99L)
                .collectList().block();

        assertNotNull(results);
        assertEquals(com.qiji.cps.module.ai.enums.ErrorCodeConstants.CHAT_STREAM_ERROR.getCode(),
                results.get(results.size() - 1).getCode());
        verify(conversationService).getOwnedConversation(7L, "MEMBER", 99L);
    }

    @Test
    void sendMessage_rejectsUnboundToolIntentBeforePersistingMessage() {
        AiChatConversationDO conversation = new AiChatConversationDO().setId(7L).setRoleId(10L);
        when(conversationService.getOwnedConversation(7L, "ADMIN", 42L)).thenReturn(conversation);
        when(toolActionService.requireAllowedAction(conversation, "CREATE_TOKEN_EXCHANGE"))
                .thenThrow(new IllegalArgumentException("not allowed"));

        assertThrows(IllegalArgumentException.class, () -> messageService.sendMessage(
                new AiChatMessageSendReqVO().setConversationId(7L).setContent("exchange")
                        .setToolIntent("CREATE_TOKEN_EXCHANGE"), 42L));

        verifyNoInteractions(messageMapper);
    }

    @Test
    void sendMessage_requiresRequestIdForAssetWriteIntent() {
        AiChatConversationDO conversation = new AiChatConversationDO().setId(7L).setRoleId(10L);
        when(conversationService.getOwnedConversation(7L, "ADMIN", 42L)).thenReturn(conversation);
        when(toolActionService.requireAllowedAction(conversation, "CREATE_TOKEN_EXCHANGE"))
                .thenReturn(new AiChatToolAction().setIntent("CREATE_TOKEN_EXCHANGE")
                        .setRiskLevel(AiChatToolRiskLevel.ASSET_WRITE));

        assertThrows(RuntimeException.class, () -> messageService.sendMessage(
                new AiChatMessageSendReqVO().setConversationId(7L).setContent("exchange")
                        .setToolIntent("CREATE_TOKEN_EXCHANGE"), 42L));

        verifyNoInteractions(messageMapper);
    }

    @Test
    void sendMessageStream_emitsAcceptedAndPreparingEventsBeforeDeferredWork() {
        List<com.qiji.cps.framework.common.pojo.CommonResult<com.qiji.cps.module.ai.controller.admin.chat.vo.message.AiChatMessageSendRespVO>> results =
                messageService.sendChatMessageStream(
                                new AiChatMessageSendReqVO().setConversationId(7L).setContent("hi"),
                                "ADMIN", 42L)
                        .take(2)
                        .collectList()
                        .block();

        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("TASK_ACCEPTED", results.get(0).getData().getEventType());
        assertEquals("TASK_PROGRESS", results.get(1).getData().getEventType());
        assertEquals("PREPARING", results.get(1).getData().getStage());
        assertEquals(results.get(0).getData().getRequestId(), results.get(1).getData().getRequestId());
        verifyNoInteractions(conversationService, messageMapper, toolActionService);
    }

    @Test
    void sendMessageStream_restoresRequestTenantForDeferredConversationLookup() {
        TenantContextHolder.setTenantId(7L);
        when(conversationService.getOwnedConversation(7L, "ADMIN", 42L)).thenAnswer(invocation -> {
            assertEquals(7L, TenantContextHolder.getRequiredTenantId());
            throw exception(CHAT_CONVERSATION_NOT_EXISTS);
        });

        var result = messageService.sendChatMessageStream(
                new AiChatMessageSendReqVO().setConversationId(7L).setContent("hi"), "ADMIN", 42L);
        TenantContextHolder.clear();

        var results = result.collectList().block();
        assertNotNull(results);
        assertEquals(com.qiji.cps.module.ai.enums.ErrorCodeConstants.CHAT_STREAM_ERROR.getCode(),
                results.get(results.size() - 1).getCode());
        assertNull(TenantContextHolder.getTenantId());
        verify(conversationService).getOwnedConversation(7L, "ADMIN", 42L);
    }

}

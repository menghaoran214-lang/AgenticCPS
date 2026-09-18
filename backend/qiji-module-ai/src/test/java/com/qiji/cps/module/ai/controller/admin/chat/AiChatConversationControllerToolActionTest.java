package com.qiji.cps.module.ai.controller.admin.chat;

import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.framework.security.core.util.SecurityFrameworkUtils;
import com.qiji.cps.framework.test.core.ut.BaseMockitoUnitTest;
import com.qiji.cps.module.ai.controller.admin.chat.vo.conversation.AiChatConversationCreateMyReqVO;
import com.qiji.cps.module.ai.controller.admin.chat.vo.conversation.AiChatConversationPageReqVO;
import com.qiji.cps.module.ai.dal.dataobject.chat.AiChatConversationDO;
import com.qiji.cps.module.ai.service.chat.AiChatConversationService;
import com.qiji.cps.module.ai.service.chat.AiChatMessageService;
import com.qiji.cps.module.ai.service.chat.tool.AiChatToolAction;
import com.qiji.cps.module.ai.service.chat.tool.AiChatToolActionService;
import com.qiji.cps.module.ai.service.chat.tool.AiChatToolInteractionType;
import com.qiji.cps.module.ai.service.chat.tool.AiChatToolRiskLevel;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatConversationControllerToolActionTest extends BaseMockitoUnitTest {

    @InjectMocks
    private AiChatConversationController controller;
    @Mock
    private AiChatConversationService conversationService;
    @Mock
    private AiChatMessageService chatMessageService;
    @Mock
    private AiChatToolActionService actionService;

    @Test
    void createConversation_returnsSnowflakeIdAsString() {
        long conversationId = 1781604279872581799L;
        when(conversationService.createChatConversationMy(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(42L))).thenReturn(conversationId);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(42L);
            var result = controller.createChatConversationMy(new AiChatConversationCreateMyReqVO());
            assertEquals("1781604279872581799", result.getData());
        }
    }

    @Test
    void getConversationList_returnsSnowflakeIdAsString() {
        long conversationId = 1781604279872581799L;
        when(conversationService.getChatConversationListByUserId(42L))
                .thenReturn(List.of(new AiChatConversationDO().setId(conversationId)));

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(42L);
            var result = controller.getChatConversationMyList();
            assertEquals("1781604279872581799", result.getData().get(0).getId());
        }
    }

    @Test
    void getConversationPage_keepsMessageCountWhenIdIsString() {
        long conversationId = 1781604279872581799L;
        when(conversationService.getChatConversationPage(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageResult<>(List.of(new AiChatConversationDO().setId(conversationId)), 1L));
        when(chatMessageService.getChatMessageCountMap(List.of(conversationId))).thenReturn(Map.of(conversationId, 3));

        var result = controller.getChatConversationPage(new AiChatConversationPageReqVO());

        assertEquals("1781604279872581799", result.getData().getList().get(0).getId());
        assertEquals(3, result.getData().getList().get(0).getMessageCount());
    }

    @Test
    void getToolActions_checksAdminOwnershipAndNeverExposesToolName() {
        AiChatConversationDO conversation = new AiChatConversationDO().setId(7L).setRoleId(10L);
        when(conversationService.getOwnedConversation(7L, "ADMIN", 42L)).thenReturn(conversation);
        when(actionService.getAvailableActions(conversation)).thenReturn(List.of(new AiChatToolAction()
                .setIntent("SEARCH_GOODS").setToolName("cps_search_goods").setLabel("搜商品")
                .setGroup("COMMON").setRiskLevel(AiChatToolRiskLevel.READ_ONLY)
                .setInteractionType(AiChatToolInteractionType.FORM).setRunningMessage("正在搜索商品")
                .setSuccessMessage("商品搜索完成").setPromptTemplate("帮我搜索商品").setFields(List.of())));

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(42L);
            var result = controller.getToolActions(7L);
            assertEquals("SEARCH_GOODS", result.getData().get(0).getIntent());
            assertFalse(Arrays.stream(result.getData().get(0).getClass().getDeclaredFields())
                    .anyMatch(field -> "toolName".equals(field.getName())));
        }

        verify(conversationService).getOwnedConversation(7L, "ADMIN", 42L);
    }
}

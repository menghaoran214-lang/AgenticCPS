package com.qiji.cps.module.ai.framework.ai.config;

import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeChatAutoConfiguration;
import com.qiji.cps.module.ai.enums.model.AiPlatformEnum;
import com.qiji.cps.module.ai.framework.ai.core.model.AiModelFactoryImpl;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DashScopeSpringAiCompatibilityTest {

    @Test
    void resolvesDashScopeAutoConfigurationMethodSignatures() {
        assertDoesNotThrow(() -> {
            for (var method : DashScopeChatAutoConfiguration.class.getDeclaredMethods()) {
                method.getGenericParameterTypes();
            }
        });
    }

    @Test
    void tongYiChatUsesSpringAiGaCompatibleClient() {
        var chatModel = new AiModelFactoryImpl().getOrCreateChatModel(
                AiPlatformEnum.TONG_YI, "test-key", null);

        OpenAiChatModel openAiChatModel = assertInstanceOf(OpenAiChatModel.class, chatModel);
        OpenAiChatOptions options = assertInstanceOf(OpenAiChatOptions.class, openAiChatModel.getOptions());
        assertEquals(
                "https://dashscope.aliyuncs.com/compatible-mode/v1", options.getBaseUrl());
        assertNotNull(chatModel);
        assertDoesNotThrow(() -> ((StreamingChatModel) chatModel)
                .stream(new Prompt(new UserMessage("test"))));
    }

    @Test
    void tongYiChatConvertsNativeDashScopeUrlToCompatibleEndpoint() {
        var chatModel = new AiModelFactoryImpl().getOrCreateChatModel(
                AiPlatformEnum.TONG_YI, "another-test-key", "https://dashscope.aliyuncs.com/api/v1");

        OpenAiChatOptions options = assertInstanceOf(OpenAiChatOptions.class, chatModel.getOptions());
        assertEquals(
                "https://dashscope.aliyuncs.com/compatible-mode/v1", options.getBaseUrl());
    }

}

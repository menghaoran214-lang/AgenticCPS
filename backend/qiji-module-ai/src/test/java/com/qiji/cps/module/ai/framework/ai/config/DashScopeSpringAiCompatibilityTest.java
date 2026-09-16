package com.qiji.cps.module.ai.framework.ai.config;

import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeChatAutoConfiguration;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
    void createsDashScopeChatModelAgainstSpringAiGa() {
        DashScopeChatModel chatModel = DashScopeChatModel.builder()
                .dashScopeApi(DashScopeApi.builder().apiKey("test-key").build())
                .build();

        assertNotNull(chatModel);
    }

}

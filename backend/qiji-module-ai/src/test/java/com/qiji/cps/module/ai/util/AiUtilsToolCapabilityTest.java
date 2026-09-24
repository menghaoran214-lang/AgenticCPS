package com.qiji.cps.module.ai.util;

import com.qiji.cps.framework.test.core.ut.BaseMockitoUnitTest;
import com.qiji.cps.module.ai.enums.model.AiPlatformEnum;
import org.junit.jupiter.api.Test;
import org.springaicommunity.qianfan.QianFanChatOptions;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.qiji.cps.framework.test.core.util.AssertUtils.assertServiceException;
import static com.qiji.cps.module.ai.enums.ErrorCodeConstants.MODEL_TOOL_CALL_UNSUPPORTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class AiUtilsToolCapabilityTest extends BaseMockitoUnitTest {

    @Test
    void buildChatOptions_tongYiUsesOpenAiCompatibleOptions() {
        ChatOptions options = AiUtils.buildChatOptions(AiPlatformEnum.TONG_YI, "qwen3-max", 0.7, 1024,
                List.of(), Map.of());

        OpenAiChatOptions openAiOptions = assertInstanceOf(OpenAiChatOptions.class, options);
        assertEquals("qwen3-max", openAiOptions.getModel());
        assertEquals(Boolean.TRUE, openAiOptions.getExtraBody().get("enable_thinking"));
    }

    @Test
    void supportsToolCalling_declaresEveryPlatformExplicitly() {
        Map<AiPlatformEnum, Boolean> expected = new EnumMap<>(AiPlatformEnum.class);
        expected.put(AiPlatformEnum.TONG_YI, true);
        expected.put(AiPlatformEnum.YI_YAN, false);
        expected.put(AiPlatformEnum.DEEP_SEEK, true);
        expected.put(AiPlatformEnum.ZHI_PU, true);
        expected.put(AiPlatformEnum.XING_HUO, true);
        expected.put(AiPlatformEnum.DOU_BAO, true);
        expected.put(AiPlatformEnum.HUN_YUAN, true);
        expected.put(AiPlatformEnum.SILICON_FLOW, true);
        expected.put(AiPlatformEnum.MINI_MAX, true);
        expected.put(AiPlatformEnum.MOONSHOT, true);
        expected.put(AiPlatformEnum.BAI_CHUAN, true);
        expected.put(AiPlatformEnum.OPENAI, true);
        expected.put(AiPlatformEnum.AZURE_OPENAI, true);
        expected.put(AiPlatformEnum.ANTHROPIC, true);
        expected.put(AiPlatformEnum.GEMINI, true);
        expected.put(AiPlatformEnum.OLLAMA, true);
        expected.put(AiPlatformEnum.STABLE_DIFFUSION, false);
        expected.put(AiPlatformEnum.MIDJOURNEY, false);
        expected.put(AiPlatformEnum.SUNO, false);
        expected.put(AiPlatformEnum.GROK, true);
        expected.put(AiPlatformEnum.JEV, false);

        assertEquals(expected.keySet(), java.util.EnumSet.allOf(AiPlatformEnum.class));
        expected.forEach((platform, supported) -> assertEquals(supported, AiUtils.supportsToolCalling(platform),
                () -> platform + " tool capability must be explicit"));
    }

    @Test
    void buildChatOptions_yiYanRejectsToolsWithoutDiscardingThem() {
        ToolCallback toolCallback = mock(ToolCallback.class);

        assertServiceException(() -> AiUtils.buildChatOptions(AiPlatformEnum.YI_YAN, "model", 0.5D, 100,
                List.of(toolCallback), Map.of()), MODEL_TOOL_CALL_UNSUPPORTED, AiPlatformEnum.YI_YAN.getName());
    }

    @Test
    void buildChatOptions_yiYanWithoutToolsRemainsCompatible() {
        ChatOptions options = AiUtils.buildChatOptions(AiPlatformEnum.YI_YAN, "model", 0.5D, 100);

        assertInstanceOf(QianFanChatOptions.class, options);
    }

    @Test
    void buildChatOptions_ignoresNullToolContextEntries() {
        ToolCallback toolCallback = mock(ToolCallback.class);
        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put(AiUtils.TOOL_CONTEXT_LOGIN_USER_ID, null);
        toolContext.put(AiUtils.TOOL_CONTEXT_TENANT_ID, 7L);

        OpenAiChatOptions options = assertInstanceOf(OpenAiChatOptions.class,
                AiUtils.buildChatOptions(AiPlatformEnum.OPENAI, "gpt-4o", 0.5D, 100,
                        List.of(toolCallback), toolContext));

        assertEquals(Map.of(AiUtils.TOOL_CONTEXT_TENANT_ID, 7L), options.getToolContext());
        assertNull(toolContext.get(AiUtils.TOOL_CONTEXT_LOGIN_USER_ID));
    }

}

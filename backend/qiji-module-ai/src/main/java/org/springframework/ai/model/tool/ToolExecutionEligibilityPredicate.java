package org.springframework.ai.model.tool;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.util.Assert;

import java.util.function.BiPredicate;

/**
 * Binary compatibility contract for Spring AI Alibaba 2.0.0-M1.1.
 *
 * <p>Spring AI renamed this contract in 2.0 GA, while the latest DashScope
 * provider still links against the milestone API.</p>
 */
public interface ToolExecutionEligibilityPredicate extends BiPredicate<ChatOptions, ChatResponse> {

    default boolean isToolExecutionRequired(ChatOptions promptOptions, ChatResponse chatResponse) {
        Assert.notNull(promptOptions, "promptOptions cannot be null");
        Assert.notNull(chatResponse, "chatResponse cannot be null");
        return test(promptOptions, chatResponse);
    }

}

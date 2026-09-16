package org.springframework.ai.model.tool;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * Spring AI 2.0 GA adapter for the milestone DashScope provider.
 */
public class DefaultToolExecutionEligibilityPredicate implements ToolExecutionEligibilityPredicate {

    @Override
    public boolean test(ChatOptions promptOptions, ChatResponse chatResponse) {
        return promptOptions != null && chatResponse != null && chatResponse.hasToolCalls();
    }

}

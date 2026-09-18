package com.qiji.cps.module.ai.framework.ai.core.model;

import com.openai.core.Timeout;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenAiCompatibleClientFactoryTest {

    @Test
    void streamingTimeout_exceedsTheFormerThreeMinuteFailureWindow() {
        Timeout timeout = OpenAiCompatibleClientFactory.streamingTimeout();

        assertEquals(Duration.ofSeconds(30), timeout.connect());
        assertEquals(Duration.ofMinutes(15), timeout.read());
        assertEquals(Duration.ofMinutes(15), timeout.write());
        assertEquals(Duration.ofMinutes(15), timeout.request());
    }

}

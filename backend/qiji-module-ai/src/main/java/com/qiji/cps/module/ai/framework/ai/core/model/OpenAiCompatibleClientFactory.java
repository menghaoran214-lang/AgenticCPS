package com.qiji.cps.module.ai.framework.ai.core.model;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.Timeout;

import java.time.Duration;

/**
 * Creates OpenAI-compatible clients with timeouts suitable for long-lived chat streams.
 */
public final class OpenAiCompatibleClientFactory {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    static final Duration STREAM_TIMEOUT = Duration.ofMinutes(15);

    private OpenAiCompatibleClientFactory() {
    }

    public static OpenAIClient create(String baseUrl, String apiKey) {
        return OpenAIOkHttpClient.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .timeout(streamingTimeout())
                .build();
    }

    public static Duration streamTimeout() {
        return STREAM_TIMEOUT;
    }

    static Timeout streamingTimeout() {
        return Timeout.builder()
                .connect(CONNECT_TIMEOUT)
                .read(STREAM_TIMEOUT)
                .write(STREAM_TIMEOUT)
                .request(STREAM_TIMEOUT)
                .build();
    }

}

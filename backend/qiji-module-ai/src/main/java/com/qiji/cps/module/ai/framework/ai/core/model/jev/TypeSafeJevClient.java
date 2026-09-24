package com.qiji.cps.module.ai.framework.ai.core.model.jev;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.qiji.cps.framework.common.util.json.JsonUtils;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.Map;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * TypeSafe AI Jev client.
 *
 * <p>Jev is a structured evaluation model and deliberately does not implement
 * Spring AI's {@code ChatModel}. This client exposes TypeSafe's native
 * System One {@code /v1/systemone} contract.</p>
 */
public final class TypeSafeJevClient {

    public static final String DEFAULT_BASE_URL = "https://api.typesafe.ai";
    public static final String DEFAULT_MODEL = "jev-latest";
    public static final String SYSTEM_ONE_PATH = "/v1/systemone";
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MILLIS = 100L;
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 60_000;

    private final RestClient restClient;

    public TypeSafeJevClient(String apiKey, String baseUrl) {
        this(apiKey, baseUrl, RestClient.builder(), true);
    }

    public TypeSafeJevClient(String apiKey, String baseUrl, RestClient.Builder restClientBuilder) {
        this(apiKey, baseUrl, restClientBuilder, false);
    }

    private TypeSafeJevClient(String apiKey, String baseUrl, RestClient.Builder restClientBuilder,
                              boolean useDefaultRequestFactory) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("TypeSafe API key must not be blank");
        }
        RestClient.Builder builder = restClientBuilder.baseUrl(normalizeBaseUrl(baseUrl));
        if (useDefaultRequestFactory) {
            builder.requestFactory(requestFactory());
        }
        this.restClient = builder
                .defaultHeaders(headers -> {
                    headers.setBearerAuth(apiKey);
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
                })
                .build();
    }

    /**
     * Evaluate a state against named Jev questions.
     *
     * @param state arbitrary JSON-compatible state supplied to Jev
     * @param questions named questions and their definitions
     * @return structured answers and usage returned by TypeSafe
     */
    public JevResponse evaluate(Object state, Map<String, JevQuestion> questions) {
        return evaluate(new JevRequest(state, DEFAULT_MODEL, questions));
    }

    public JevResponse evaluate(JevRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Jev request must not be null");
        }
        if (request.questions() == null || request.questions().isEmpty()) {
            throw new IllegalArgumentException("Jev questions must not be empty");
        }
        for (int attempt = 1; ; attempt++) {
            try {
                String responseBody = restClient.post()
                        .uri(SYSTEM_ONE_PATH)
                        .body(JsonUtils.toJsonString(request))
                        .retrieve()
                        .body(String.class);
                return parseResponse(responseBody);
            } catch (RestClientResponseException ex) {
                if (!isRetryable(ex.getStatusCode().value()) || attempt >= MAX_ATTEMPTS) {
                    throw new TypeSafeJevException("TypeSafe Jev request failed with HTTP "
                            + ex.getStatusCode().value());
                }
                sleepBeforeRetry(ex, attempt);
            }
        }
    }

    private static JevResponse parseResponse(String responseBody) {
        try {
            return JsonUtils.getObjectMapper().readValue(responseBody, JevResponse.class);
        } catch (IOException ex) {
            throw new TypeSafeJevException("TypeSafe Jev response could not be parsed");
        }
    }

    static String normalizeBaseUrl(String baseUrl) {
        String value = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith(SYSTEM_ONE_PATH)) {
            value = value.substring(0, value.length() - SYSTEM_ONE_PATH.length());
        } else if (value.endsWith("/v1")) {
            value = value.substring(0, value.length() - "/v1".length());
        }
        return value;
    }

    private static boolean isRetryable(int status) {
        return status == 429 || status == 529;
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        factory.setReadTimeout(READ_TIMEOUT_MILLIS);
        return factory;
    }

    private static void sleepBeforeRetry(RestClientResponseException ex, int attempt) {
        long delayMillis = retryAfterMillis(ex, attempt);
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new TypeSafeJevException("TypeSafe Jev retry interrupted", interrupted);
        }
    }

    private static long retryAfterMillis(RestClientResponseException ex, int attempt) {
        String retryAfter = ex.getResponseHeaders().getFirst("Retry-After");
        if (retryAfter != null) {
            try {
                long seconds = Long.parseLong(retryAfter.trim());
                if (seconds <= 0) {
                    return 0L;
                }
                return Math.min(2_000L, seconds > 2 ? 2_000L : seconds * 1_000L);
            } catch (NumberFormatException ignored) {
                // Fall back to the bounded exponential delay for malformed headers.
            }
        }
        return Math.min(2_000L, RETRY_BACKOFF_MILLIS << (attempt - 1));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JevRequest(
            @JsonProperty("state") Object state,
            @JsonProperty("model") String model,
            @JsonProperty("questions") Map<String, JevQuestion> questions) {

        public JevRequest {
            model = model == null || model.isBlank() ? DEFAULT_MODEL : model;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JevQuestion(
            @JsonProperty("type") String type,
            @JsonProperty("instructions") Object instructions,
            @JsonProperty("criteria") Object criteria) {

        public JevQuestion(String type, String instructions) {
            this(type, instructions, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JevResponse(
            @JsonProperty("id") String id,
            @JsonProperty("model") String model,
            @JsonProperty("provider") String provider,
            @JsonProperty("answers") Map<String, Object> answers,
            @JsonProperty("confidence") Object confidence,
            @JsonProperty("usage") Map<String, Object> usage,
            @JsonProperty("task_id") String taskId,
            @JsonProperty("consumed") Object consumed) {
    }

    public static final class TypeSafeJevException extends RuntimeException {
        public TypeSafeJevException(String message) {
            super(message);
        }

        public TypeSafeJevException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

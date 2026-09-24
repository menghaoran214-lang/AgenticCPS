package com.qiji.cps.module.ai.framework.ai.core.model.jev;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class TypeSafeJevClientTest {

    private MockRestServiceServer server;
    private RestClient.Builder builder;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @AfterEach
    void tearDown() {
        server.verify();
    }

    @Test
    void evaluate_sendsTypeSafeSystemOneContractAndParsesResponse() {
        server.expect(once(), requestTo("http://localhost" + TypeSafeJevClient.SYSTEM_ONE_PATH))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/json"))
                .andExpect(header(HttpHeaders.ACCEPT, "application/json"))
                .andExpect(jsonPath("$.model").value("jev-latest"))
                .andExpect(jsonPath("$.state.message").value("payout failed"))
                .andExpect(jsonPath("$.questions.urgent.type").value("choice"))
                .andExpect(jsonPath("$.questions.urgent.criteria.frontend").value("Rendering issue"))
                .andRespond(withStatus(OK).contentType(APPLICATION_JSON).body(
                        "{\"id\":\"decision-1\",\"model\":\"jev-1.13.0\",\"provider\":\"typesafe\","
                                + "\"answers\":{\"urgent\":{\"type\":\"noul\",\"noul\":0.95,\"confidence\":0.99}},"
                                + "\"usage\":{\"input_tokens\":296,\"output_tokens\":20},"
                                + "\"task_id\":\"task-1\",\"consumed\":true}"));

        TypeSafeJevClient.JevResponse response = new TypeSafeJevClient("test-key", "http://localhost", builder)
                .evaluate(Map.of("message", "payout failed"),
                        Map.of("urgent", new TypeSafeJevClient.JevQuestion("choice", "Which team owns this?",
                                Map.of("frontend", "Rendering issue", "payments", "Payment issue"))));

        assertEquals("jev-1.13.0", response.model());
        assertEquals("decision-1", response.id());
        assertEquals("typesafe", response.provider());
        assertEquals("task-1", response.taskId());
        assertEquals(Boolean.TRUE, response.consumed());
        Map<?, ?> urgent = (Map<?, ?>) response.answers().get("urgent");
        assertEquals(0.95, ((Number) urgent.get("noul")).doubleValue());
        assertEquals(296, ((Number) response.usage().get("input_tokens")).intValue());
    }

    @Test
    void evaluate_retriesRateLimitAtMostThreeAttempts() {
        AtomicInteger attempts = new AtomicInteger();
        server.expect(times(3), requestTo("http://localhost" + TypeSafeJevClient.SYSTEM_ONE_PATH))
                .andExpect(jsonPath("$.questions.q.criteria").doesNotExist())
                .andRespond(request -> {
                    if (attempts.incrementAndGet() < 3) {
                        return withStatus(TOO_MANY_REQUESTS).createResponse(request);
                    }
                    return withStatus(OK).contentType(APPLICATION_JSON)
                            .body("{\"model\":\"jev-1.13.0\",\"answers\":{},\"usage\":{}}")
                            .createResponse(request);
                });

        TypeSafeJevClient.JevResponse response = new TypeSafeJevClient("key", "http://localhost", builder)
                .evaluate("state", Map.of("q", new TypeSafeJevClient.JevQuestion("noul", "question")));

        assertNotNull(response);
        assertEquals(3, attempts.get());
    }

    @Test
    void evaluate_doesNotRetryAuthenticationFailure() {
        server.expect(once(), requestTo("http://localhost" + TypeSafeJevClient.SYSTEM_ONE_PATH))
                .andRespond(withStatus(UNAUTHORIZED).contentType(APPLICATION_JSON)
                        .body("{\"error\":\"unauthorized\"}"));

        TypeSafeJevClient.TypeSafeJevException error = assertThrows(
                TypeSafeJevClient.TypeSafeJevException.class,
                () -> new TypeSafeJevClient("secret", "http://localhost", builder).evaluate(
                        "state", Map.of("q", new TypeSafeJevClient.JevQuestion("noul", "question"))));

        assertTrue(error.getMessage().contains("401"));
        assertFalse(error.getMessage().contains("secret"));
    }

    @Test
    void normalizeBaseUrl_removesTrailingSlashesAndUsesDefault() {
        assertEquals("http://localhost", TypeSafeJevClient.normalizeBaseUrl("http://localhost///"));
        assertEquals("http://localhost", TypeSafeJevClient.normalizeBaseUrl("http://localhost/v1/systemone"));
        assertEquals("http://localhost", TypeSafeJevClient.normalizeBaseUrl("http://localhost/v1"));
        assertEquals(TypeSafeJevClient.DEFAULT_BASE_URL, TypeSafeJevClient.normalizeBaseUrl("  "));
    }
}

package com.qiji.cps.module.ai.framework.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class McpSseMessageEndpointConfigurationTest {

    @Test
    void shouldReturnHelpfulMethodNotAllowedForBrowserGetOnMessageEndpoint() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.mcp.server.sse-endpoint", "/sse")
                .withProperty("spring.ai.mcp.server.sse-message-endpoint", "/mcp/message");

        MockMvc mockMvc = MockMvcBuilders.routerFunctions(
                new McpSseMessageEndpointConfiguration().mcpSseMessageEndpointGetGuard(environment))
                .build();

        mockMvc.perform(get("/mcp/message"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, "POST"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error", containsString("POST")))
                .andExpect(jsonPath("$.sseEndpoint").value("/sse"))
                .andExpect(jsonPath("$.messageEndpoint").value("/mcp/message"));
    }

    @Test
    void shouldLeavePostMessageRequestsToTheMcpHandler() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.mcp.server.sse-endpoint", "/sse")
                .withProperty("spring.ai.mcp.server.sse-message-endpoint", "/mcp/message");

        MockMvc mockMvc = MockMvcBuilders.routerFunctions(
                new McpSseMessageEndpointConfiguration().mcpSseMessageEndpointGetGuard(environment))
                .build();

        // The guard is deliberately GET-only; the actual MCP POST handler must own this route.
        mockMvc.perform(post("/mcp/message"))
                .andExpect(status().isNotFound());
    }
}

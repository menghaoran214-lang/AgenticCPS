package com.qiji.cps.module.ai.framework.ai.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.web.servlet.function.RequestPredicates.GET;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.springframework.ai.mcp.server.webmvc.transport.WebMvcSseServerTransportProvider")
@ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = "enabled", havingValue = "true")
public class McpSseMessageEndpointConfiguration {

    @Bean
    public RouterFunction<ServerResponse> mcpSseMessageEndpointGetGuard(Environment environment) {
        String sseEndpoint = environment.getProperty("spring.ai.mcp.server.sse-endpoint", "/sse");
        String messageEndpoint = environment.getProperty("spring.ai.mcp.server.sse-message-endpoint", "/mcp/message");
        return RouterFunctions.route(GET(messageEndpoint), request -> {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("error", "MCP SSE message endpoint only accepts POST requests from an MCP client. Use the SSE endpoint for browser or connectivity checks.");
            body.put("sseEndpoint", sseEndpoint);
            body.put("messageEndpoint", messageEndpoint);

            return ServerResponse.status(HttpStatus.METHOD_NOT_ALLOWED)
                    .header(HttpHeaders.ALLOW, HttpMethod.POST.name())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
        });
    }
}

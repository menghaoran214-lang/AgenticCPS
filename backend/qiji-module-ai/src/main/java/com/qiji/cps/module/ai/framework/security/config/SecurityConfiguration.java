package com.qiji.cps.module.ai.framework.security.config;

import com.qiji.cps.framework.security.config.AuthorizeRequestsCustomizer;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * AI 模块的 Security 配置
 */
@Configuration(proxyBeanMethods = false, value = "aiSecurityConfiguration")
public class SecurityConfiguration {

    @Resource
    private Environment environment;

    @Bean("aiAuthorizeRequestsCustomizer")
    public AuthorizeRequestsCustomizer authorizeRequestsCustomizer() {
        return new AuthorizeRequestsCustomizer() {

            @Override
            public void customize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
                registry.requestMatchers(environment.getProperty("spring.ai.mcp.server.sse-endpoint", "/sse")).permitAll();
                registry.requestMatchers(environment.getProperty("spring.ai.mcp.server.sse-message-endpoint", "/mcp/message")).permitAll();
                registry.requestMatchers(environment.getProperty("spring.ai.mcp.server.streamable-http.mcp-endpoint", "/mcp")).permitAll();
            }

        };
    }

}

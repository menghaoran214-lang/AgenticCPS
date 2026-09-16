package org.springframework.boot.web.client;

/**
 * Compatibility alias for starters compiled against the Spring Boot 3 package.
 * Spring Boot 4 moved this type to {@code org.springframework.boot.restclient}.
 */
@Deprecated
public class RestTemplateBuilder extends org.springframework.boot.restclient.RestTemplateBuilder {

    public RestTemplateBuilder() {
        super();
    }

    public RestTemplateBuilder(org.springframework.boot.restclient.RestTemplateCustomizer... customizers) {
        super(customizers);
    }

}

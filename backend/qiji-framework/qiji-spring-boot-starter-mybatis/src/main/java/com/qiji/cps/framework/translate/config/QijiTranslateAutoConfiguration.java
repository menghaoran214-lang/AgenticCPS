package com.qiji.cps.framework.translate.config;

import com.qiji.cps.framework.translate.core.TranslateUtils;
import com.fhs.trans.service.impl.TransService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class QijiTranslateAutoConfiguration {

    @Bean
    @SuppressWarnings({"InstantiationOfUtilityClass", "SpringJavaInjectionPointsAutowiringInspection"})
    public TranslateUtils translateUtils(TransService transService) {
        TranslateUtils.init(transService);
        return new TranslateUtils();
    }

    /**
     * Easy-Trans 3.x still expects the Spring Boot 3 RestTemplateBuilder type.
     * The compatibility alias keeps its auto-configuration usable on Boot 4.
     */
    @Bean
    @ConditionalOnMissingBean(org.springframework.boot.web.client.RestTemplateBuilder.class)
    public org.springframework.boot.web.client.RestTemplateBuilder legacyRestTemplateBuilder() {
        return new org.springframework.boot.web.client.RestTemplateBuilder();
    }

}

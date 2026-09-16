package com.qiji.cps.module.report.framework.jmreport.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JmReportConfigurationTest {

    private final Pattern incompatibleComponents =
            Pattern.compile(JmReportConfiguration.SPRING_BOOT_4_INCOMPATIBLE_AI_COMPONENTS);

    @Test
    void shouldExcludeOnlyJimuReportAiComponentsThatUseRemovedSpringApi() {
        assertTrue(incompatibleComponents.matcher("org.jeecg.modules.jmreport.ai.a.a").matches());
        assertTrue(incompatibleComponents.matcher("org.jeecg.modules.jmreport.ai.service.a.a").matches());

        assertFalse(incompatibleComponents.matcher("org.jeecg.modules.jmreport.desreport.a.a").matches());
        assertFalse(incompatibleComponents.matcher("org.jeecg.modules.jmreport.ai.service.IJimuAiAssistantService").matches());
    }

    @Test
    void shouldRemoveAiBeansRegisteredByJimuReportAutoConfiguration() {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registry.registerBeanDefinition(JmReportConfiguration.JIMU_AI_CONTROLLER_BEAN_NAME,
                new RootBeanDefinition(Object.class));
        registry.registerBeanDefinition(JmReportConfiguration.JIMU_AI_SERVICE_BEAN_NAME,
                new RootBeanDefinition(Object.class));
        registry.registerBeanDefinition("jimuReportController", new RootBeanDefinition(Object.class));

        JmReportConfiguration.jimuReportSpringBoot4CompatibilityPostProcessor()
                .postProcessBeanDefinitionRegistry(registry);

        assertFalse(registry.containsBeanDefinition(JmReportConfiguration.JIMU_AI_CONTROLLER_BEAN_NAME));
        assertFalse(registry.containsBeanDefinition(JmReportConfiguration.JIMU_AI_SERVICE_BEAN_NAME));
        assertTrue(registry.containsBeanDefinition("jimuReportController"));
    }
}

package com.qiji.cps.module.ai.framework.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.retry.support.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class AiAutoConfigurationRetryCompatibilityTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AiAutoConfiguration.class);

    @Test
    void providesLegacyRetryTemplateRequiredByQianFan() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(RetryTemplate.class));
    }

    @Test
    void preservesApplicationProvidedLegacyRetryTemplate() {
        RetryTemplate custom = new RetryTemplate();
        contextRunner.withBean("customRetryTemplate", RetryTemplate.class, () -> custom)
                .run(context -> assertThat(context.getBean(RetryTemplate.class)).isSameAs(custom));
    }
}

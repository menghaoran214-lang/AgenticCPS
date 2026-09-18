package com.qiji.cps.framework.web.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class QijiWebAutoConfigurationTest {

    @Test
    void configuresBoundedExecutorForMvcStreamingResponses() {
        QijiWebAutoConfiguration configuration = new QijiWebAutoConfiguration();
        ThreadPoolTaskExecutor executor = configuration.mvcAsyncTaskExecutor();
        WebMvcConfigurer configurer = configuration.mvcAsyncWebMvcConfigurer(executor);
        AsyncSupportConfigurer asyncSupport = mock(AsyncSupportConfigurer.class);

        configurer.configureAsyncSupport(asyncSupport);

        assertEquals("mvc-async-", executor.getThreadNamePrefix());
        assertEquals(8, executor.getCorePoolSize());
        assertEquals(32, executor.getMaxPoolSize());
        assertEquals(500, executor.getQueueCapacity());
        verify(asyncSupport).setTaskExecutor(executor);
        verify(asyncSupport).setDefaultTimeout(30 * 60 * 1000L);
    }

}

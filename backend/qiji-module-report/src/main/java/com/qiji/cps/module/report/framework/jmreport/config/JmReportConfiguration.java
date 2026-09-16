package com.qiji.cps.module.report.framework.jmreport.config;

import com.qiji.cps.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import com.qiji.cps.framework.common.biz.system.permission.PermissionCommonApi;
import com.qiji.cps.framework.security.config.SecurityProperties;
import com.qiji.cps.module.report.framework.jmreport.core.service.JmOnlDragExternalServiceImpl;
import com.qiji.cps.module.report.framework.jmreport.core.service.JmReportTokenServiceImpl;
import com.qiji.cps.module.system.api.permission.PermissionApi;
import org.jeecg.modules.jmreport.api.JmReportTokenServiceI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;

/**
 * 积木报表的配置类
 *
 * @author AgenticCPS源码
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(
        basePackages = "org.jeecg.modules.jmreport",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = JmReportConfiguration.SPRING_BOOT_4_INCOMPATIBLE_AI_COMPONENTS))
public class JmReportConfiguration {

    static final String JIMU_AI_CONTROLLER_BEAN_NAME = "jimuAiAssistantController";
    static final String JIMU_AI_SERVICE_BEAN_NAME = "jimuAiAssistantServiceImpl";

    /**
     * JimuReport 2.1.3 invokes the Spring 6-only
     * HttpComponentsClientHttpRequestFactory#setConnectTimeout(int) method from its AI service.
     * Keep the report engine enabled on Spring 7 while excluding only that AI endpoint and service.
     */
    static final String SPRING_BOOT_4_INCOMPATIBLE_AI_COMPONENTS =
            "org\\.jeecg\\.modules\\.jmreport\\.ai\\.(?:a\\.a|service\\.a\\.a)";

    @Bean
    static BeanDefinitionRegistryPostProcessor jimuReportSpringBoot4CompatibilityPostProcessor() {
        return new BeanDefinitionRegistryPostProcessor() {
            @Override
            public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
                removeIfPresent(registry, JIMU_AI_CONTROLLER_BEAN_NAME);
                removeIfPresent(registry, JIMU_AI_SERVICE_BEAN_NAME);
            }

            @Override
            public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
                // No bean instance processing is required.
            }
        };
    }

    private static void removeIfPresent(BeanDefinitionRegistry registry, String beanName) {
        if (registry.containsBeanDefinition(beanName)) {
            registry.removeBeanDefinition(beanName);
        }
    }

    @Bean
    public JmReportTokenServiceI jmReportTokenService(OAuth2TokenCommonApi oAuth2TokenApi,
                                                      PermissionCommonApi permissionApi,
                                                      SecurityProperties securityProperties) {
        return new JmReportTokenServiceImpl(oAuth2TokenApi, permissionApi, securityProperties);
    }

    @Bean // 暂时注释：可以按需实现后打开
    @Primary
    public JmOnlDragExternalServiceImpl jmOnlDragExternalService2() {
        return new JmOnlDragExternalServiceImpl();
    }

}

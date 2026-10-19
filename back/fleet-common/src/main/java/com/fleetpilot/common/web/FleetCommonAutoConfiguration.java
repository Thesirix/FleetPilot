package com.fleetpilot.common.web;

import com.fleetpilot.common.error.ApiExceptionHandler;
import com.fleetpilot.common.error.PersistenceExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers the shared web building blocks in every servlet-based FleetPilot service, with no configuration needed:
 * the correlation id filter (first in the chain) and the problem-detail exception handler.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class FleetCommonAutoConfiguration {

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiExceptionHandler apiExceptionHandler() {
        return new ApiExceptionHandler();
    }

    /** Only in services that use Spring Data (the optional dependencies are then on the classpath). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {"org.springframework.dao.DataIntegrityViolationException",
        "org.springframework.data.core.PropertyReferenceException"})
    static class PersistenceErrors {

        @Bean
        @ConditionalOnMissingBean
        PersistenceExceptionHandler persistenceExceptionHandler() {
            return new PersistenceExceptionHandler();
        }
    }
}

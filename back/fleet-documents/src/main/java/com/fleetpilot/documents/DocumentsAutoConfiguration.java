package com.fleetpilot.documents;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.thymeleaf.TemplateEngine;

/** Provides the {@link PdfRenderer} on top of the Thymeleaf engine configured by Spring Boot. */
@AutoConfiguration(afterName = "org.springframework.boot.thymeleaf.autoconfigure.ThymeleafAutoConfiguration")
@ConditionalOnBean(TemplateEngine.class)
public class DocumentsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PdfRenderer pdfRenderer(TemplateEngine templates) {
        return new PdfRenderer(templates);
    }
}

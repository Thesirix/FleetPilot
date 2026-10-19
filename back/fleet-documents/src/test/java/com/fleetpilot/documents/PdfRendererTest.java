package com.fleetpilot.documents;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.StringTemplateResolver;

@DisplayName("PDF documents")
class PdfRendererTest {

    @Test
    void a_template_becomes_a_pdf_with_the_shared_style_and_french_characters() {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(new StringTemplateResolver());
        String template = """
                <html xmlns:th="http://www.thymeleaf.org"><head><style th:utext="${documentStyles}"></style></head>
                <body><h1 th:text="${title}">x</h1><p>Échéance, 1 250,00 €</p></body></html>""";

        byte[] pdf = new PdfRenderer(engine).render(template, Map.of("title", "Bon de commande DEV-2026-000123"),
                Locale.FRANCE);

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(1_000);
    }

    @Test
    void the_shared_stylesheet_is_on_the_classpath() {
        assertThat(PdfRenderer.stylesheet()).contains("@page", "#0f766e");
    }

    @Test
    void amounts_quantities_and_dates_are_written_the_french_way() {
        DocumentFormats fmt = DocumentFormats.INSTANCE;
        assertThat(fmt.money(1250)).isEqualTo("1 250,00 €");
        assertThat(fmt.roundMoney(1250.6)).isEqualTo("1 251 €");
        assertThat(fmt.quantity(28.0)).isEqualTo("28");
        assertThat(fmt.quantity(4.1)).isEqualTo("4,1");
        assertThat(fmt.quantity(0.25)).isEqualTo("0,25");
        assertThat(fmt.percent(8.5)).isEqualTo("8,5 %");
        assertThat(fmt.number(118000L)).isEqualTo("118 000");
        assertThat(fmt.date(LocalDate.of(2026, 11, 2))).isEqualTo("2 novembre 2026");
        assertThat(fmt.dateTime("2026-10-14T08:30:00Z")).isEqualTo("14 octobre 2026 à 10h30");
        assertThat(fmt.withVat("100")).isEqualTo("120,00 €");
        assertThat(fmt.money(null)).isEqualTo("-");
        assertThat(fmt.month("2026-09")).isEqualTo("septembre 2026");
    }
}

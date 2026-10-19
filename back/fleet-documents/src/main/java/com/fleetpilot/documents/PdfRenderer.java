package com.fleetpilot.documents;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Renders a Thymeleaf template (XHTML) into a PDF document. The shared FleetPilot document style is given to every
 * template as the {@value #STYLES} variable, to be written in the head with
 * {@code <style th:utext="${documentStyles}"></style>}: one look for purchase orders, reports and every other document.
 */
public class PdfRenderer {

    /** Model variable holding the CSS of the FleetPilot documents. */
    public static final String STYLES = "documentStyles";

    private static final String STYLESHEET = "/documents/fleetpilot-document.css";

    private final TemplateEngine templates;
    private final String styles;

    public PdfRenderer(TemplateEngine templates) {
        this.templates = templates;
        this.styles = stylesheet();
    }

    /**
     * @param template Thymeleaf template name, for example {@code pdf/purchase-order}
     * @param model    variables of the template
     * @param locale   language of the document
     */
    public byte[] render(String template, Map<String, Object> model, Locale locale) {
        Map<String, Object> variables = new HashMap<>(model);
        variables.put(STYLES, styles);
        return toPdf(templates.process(template, new Context(locale, variables)));
    }

    /** Converts well-formed XHTML into PDF. */
    public static byte[] toPdf(String xhtml) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            new PdfRendererBuilder()
                    .useFastMode()
                    .withHtmlContent(xhtml, null)
                    .toStream(out)
                    .run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("PDF rendering failed", e);
        }
    }

    static String stylesheet() {
        try (InputStream in = PdfRenderer.class.getResourceAsStream(STYLESHEET)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + STYLESHEET);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

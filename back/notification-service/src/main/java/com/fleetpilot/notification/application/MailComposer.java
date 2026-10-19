package com.fleetpilot.notification.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fleetpilot.common.messaging.EmailRequest;
import com.fleetpilot.documents.PdfRenderer;
import com.fleetpilot.notification.application.Ports.Attachment;
import com.fleetpilot.notification.application.Ports.OutgoingMail;
import com.fleetpilot.notification.application.Ports.QuoteDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Renders an e-mail from its Thymeleaf template ({@code templates/mail/<template>.html}). The subject is the
 * {@code <title>} of the rendered page, so wording and variables of an e-mail live in one file. Attachments are built
 * on demand: a purchase order is generated from the accepted quote.
 */
@Component
public class MailComposer {

    /** Attachment kind: purchase order (bon de commande) of an accepted quote, in PDF. */
    public static final String PURCHASE_ORDER = "PURCHASE_ORDER";

    private static final Locale FRENCH = Locale.FRANCE;
    private static final Pattern TITLE = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL);

    private final TemplateEngine templates;
    private final PdfRenderer pdf;
    private final QuoteDirectory quotes;
    private final String portalUrl;

    public MailComposer(TemplateEngine templates, PdfRenderer pdf, QuoteDirectory quotes,
            @Value("${fleetpilot.notifications.portal-url}") String portalUrl) {
        this.templates = templates;
        this.pdf = pdf;
        this.quotes = quotes;
        this.portalUrl = portalUrl;
    }

    public OutgoingMail compose(EmailRequest request) {
        Map<String, Object> model = new HashMap<>(request.model());
        model.put("recipientName", request.toName());
        model.put("portalUrl", portalUrl);
        model.put("fmt", MailFormats.INSTANCE);
        String html = templates.process("mail/" + request.template(), new Context(FRENCH, model));
        return new OutgoingMail(request.to(), request.toName(), subject(html), html, attachments(request));
    }

    static String subject(String html) {
        Matcher title = TITLE.matcher(html);
        if (!title.find()) {
            throw new IllegalStateException("An e-mail template must define its subject in <title>");
        }
        return title.group(1).replaceAll("\\s+", " ").trim()
                .replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"");
    }

    private List<Attachment> attachments(EmailRequest request) {
        List<Attachment> attachments = new ArrayList<>();
        for (String kind : request.attachments()) {
            if (PURCHASE_ORDER.equals(kind)) {
                attachments.add(purchaseOrder(UUID.fromString(request.relatedId())));
            } else {
                throw new IllegalArgumentException("Unknown attachment " + kind);
            }
        }
        return attachments;
    }

    private Attachment purchaseOrder(UUID quoteId) {
        Map<String, Object> quote = quotes.quote(quoteId);
        byte[] content = pdf.render("pdf/purchase-order", Map.of("quote", quote, "fmt", MailFormats.INSTANCE), FRENCH);
        return new Attachment("bon-de-commande-" + quote.get("reference") + ".pdf", "application/pdf", content);
    }
}

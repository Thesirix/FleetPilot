package com.fleetpilot.common.messaging;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * An e-mail to send, put on the {@link Queues#NOTIFICATIONS} queue by any service. The notification service renders
 * the template in the language of the recipient, builds the attachments and sends it.
 *
 * @param requestId   idempotency key: the same request received twice is sent once
 * @param template    name of the template (quote-accepted, deadline-reminder, report-ready...)
 * @param to          address of the recipient
 * @param toName      display name of the recipient, may be null
 * @param companyId   client company concerned, may be null
 * @param relatedType kind of record the e-mail is about ("quote", "vehicle", "report"...)
 * @param relatedId   id of that record
 * @param model       values used by the template
 * @param attachments documents to build and attach (for example {@code PURCHASE_ORDER})
 */
public record EmailRequest(String requestId, String template, String to, String toName, UUID companyId,
        String relatedType, String relatedId, Map<String, Object> model, List<String> attachments) {

    public EmailRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(to, "to");
        // values may be null (an optional comment...): Map.copyOf would refuse them
        model = model == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(model));
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}

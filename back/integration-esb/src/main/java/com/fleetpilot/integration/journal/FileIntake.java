package com.fleetpilot.integration.journal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fleetpilot.integration.journal.Outcome.Issue;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The steps every file route shares, called from the Camel DSL: record the file in the journal as it arrives, spot a
 * duplicate, record the outcome, a rejection or a failure. The business work stays in the handler of each flow.
 */
@Component("intake")
public class FileIntake {

    /** Exchange properties set by {@link #open(Exchange)}. */
    public static final String JOURNAL_ID = "fleetpilotJournalId";
    public static final String MESSAGE_KEY = "fleetpilotMessageKey";
    public static final String FLOW = "fleetpilotFlow";

    /** A replayed file is dropped again as {@code replay-<exchange id>__<original name>}. */
    private static final Pattern REPLAY = Pattern.compile("^replay-([0-9a-f-]{36})__(.+)$");
    private static final Logger LOG = LoggerFactory.getLogger(FileIntake.class);

    private final ExchangeJournal journal;
    private final ProcessedFiles processed;

    public FileIntake(ExchangeJournal journal, ProcessedFiles processed) {
        this.journal = journal;
        this.processed = processed;
    }

    /** Journals the file and computes the key of the idempotent consumer. The body becomes the text of the file. */
    public void open(Exchange exchange) {
        Flow flow = exchange.getProperty(FLOW, Flow.class);
        String content = exchange.getMessage().getBody(String.class);
        String name = exchange.getMessage().getHeader(Exchange.FILE_NAME_ONLY, String.class);
        UUID replayOf = null;
        Matcher replay = REPLAY.matcher(name);
        if (replay.matches()) {
            replayOf = UUID.fromString(replay.group(1));
            name = replay.group(2);
        }
        String checksum = sha256(content);
        UUID id = journal.open(flow, name, content, checksum, replayOf);
        exchange.setProperty(JOURNAL_ID, id);
        exchange.setProperty(MESSAGE_KEY, ProcessedFiles.key(flow, checksum));
        exchange.getMessage().setBody(content);
        LOG.info("{} received: {} ({} bytes)", flow, name, content.length());
    }

    public void duplicate(Exchange exchange) {
        String key = exchange.getProperty(MESSAGE_KEY, String.class);
        journal.duplicate(id(exchange), processed.firstExchange(key).orElse(null));
        LOG.info("Duplicate file ignored: {}", exchange.getMessage().getHeader(Exchange.FILE_NAME_ONLY));
    }

    /** The handler of the flow returned its outcome as the body. */
    public void complete(Exchange exchange) {
        Outcome outcome = exchange.getMessage().getBody(Outcome.class);
        journal.complete(id(exchange), outcome);
        LOG.info("{} handled: {} ({} of {} accepted)", exchange.getProperty(FLOW), outcome.summary(),
                outcome.accepted(), outcome.records());
    }

    /** The file could not be read or broke its schema: refused as a whole, with the reason. */
    public void rejectUnreadable(Exchange exchange) {
        Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        String reason = readable(cause);
        journal.complete(id(exchange), Outcome.rejected(null, Issue.error(null, null, "INVALID_FILE", reason),
                "Fichier refusé : format invalide"));
        LOG.warn("File refused: {}", reason);
    }

    /**
     * A technical failure persisted after the retries: the exchange is marked failed and its key removed from the
     * idempotent repository, so that the same file can be replayed once the cause is fixed.
     */
    public void fail(Exchange exchange) {
        Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        UUID id = exchange.getProperty(JOURNAL_ID, UUID.class);
        if (id == null) {
            LOG.error("Failure before the file was journaled", cause);
            return;
        }
        String key = exchange.getProperty(MESSAGE_KEY, String.class);
        if (key != null) {
            processed.remove(key);
        }
        journal.failed(id, readable(cause));
        LOG.error("Exchange {} failed after the retries: {}", id, readable(cause));
    }

    private static UUID id(Exchange exchange) {
        return exchange.getProperty(JOURNAL_ID, UUID.class);
    }

    /** The message of the deepest cause, which is the one a partner can act upon (line, column, value...). */
    static String readable(Throwable error) {
        if (error == null) {
            return "cause inconnue";
        }
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        return message.length() > 400 ? message.substring(0, 399) + "…" : message;
    }

    /** SHA-256 of a text, the key of the idempotent consumer. */
    public static String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

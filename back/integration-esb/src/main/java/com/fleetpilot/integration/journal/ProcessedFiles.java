package com.fleetpilot.integration.journal;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.apache.camel.Exchange;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.service.ServiceSupport;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Repository of the idempotent consumer of the file routes (Camel EIP): the key of a file is its flow and the SHA-256
 * of its content, so a partner sending the same file twice (a retry of their SFTP job, a manual resend) does not
 * create the same quotes or transactions twice. Kept in PostgreSQL: it survives restarts and is shared by several
 * instances of the bus.
 */
@Component
public class ProcessedFiles extends ServiceSupport implements IdempotentRepository {

    private final JdbcClient jdbc;
    private final Clock clock;

    public ProcessedFiles(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public static String key(Flow flow, String checksum) {
        return flow.name() + ":" + checksum;
    }

    @Override
    public boolean add(Exchange exchange, String key) {
        UUID exchangeId = exchange.getProperty(FileIntake.JOURNAL_ID, UUID.class);
        return insert(key, exchangeId);
    }

    @Override
    public boolean add(String key) {
        return insert(key, new UUID(0, 0));
    }

    private boolean insert(String key, UUID exchangeId) {
        return jdbc.sql("""
                insert into processed_file (message_key, exchange_id, processed_at) values (:key, :exchange, :at)
                on conflict (message_key) do nothing""")
                .param("key", key).param("exchange", exchangeId).param("at", Timestamp.from(clock.instant()))
                .update() == 1;
    }

    @Override
    public boolean contains(String key) {
        return firstExchange(key).isPresent();
    }

    /** The exchange that handled this content first. */
    public Optional<UUID> firstExchange(String key) {
        return jdbc.sql("select exchange_id from processed_file where message_key = :key").param("key", key)
                .query(UUID.class).optional();
    }

    @Override
    public boolean remove(String key) {
        return jdbc.sql("delete from processed_file where message_key = :key").param("key", key).update() == 1;
    }

    @Override
    public boolean confirm(String key) {
        return true;
    }

    @Override
    public void clear() {
        jdbc.sql("delete from processed_file").update();
    }
}

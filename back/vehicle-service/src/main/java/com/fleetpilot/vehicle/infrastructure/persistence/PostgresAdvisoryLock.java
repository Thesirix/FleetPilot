package com.fleetpilot.vehicle.infrastructure.persistence;

import com.fleetpilot.vehicle.application.ClusterLock;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ClusterLock} based on PostgreSQL transaction-level advisory locks: the lock is released automatically at
 * commit or rollback, even if the instance crashes, so it can never stay stuck.
 */
@Component
public class PostgresAdvisoryLock implements ClusterLock {

    private final JdbcClient jdbc;

    public PostgresAdvisoryLock(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryLock(String name) {
        return Boolean.TRUE.equals(jdbc.sql("select pg_try_advisory_xact_lock(hashtext(:name))")
                .param("name", name)
                .query(Boolean.class)
                .single());
    }
}

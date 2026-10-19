package com.fleetpilot.vehicle.domain.repository;

import java.time.Instant;
import java.util.UUID;

import com.fleetpilot.vehicle.domain.model.AuditEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, UUID> {

    /** The journal, newest first, filtered on what the screen offers; every filter but the period is optional. */
    @Query("""
            select a from AuditEntry a
            where (:companyId is null or a.companyId = :companyId)
              and (:targetType is null or a.targetType = :targetType)
              and (:actor is null or lower(a.actor) like lower(concat('%', cast(:actor as string), '%')))
              and a.occurredAt >= :since
            order by a.occurredAt desc, a.id""")
    Page<AuditEntry> search(@Param("companyId") UUID companyId, @Param("targetType") String targetType,
            @Param("actor") String actor, @Param("since") Instant since, Pageable pageable);
}

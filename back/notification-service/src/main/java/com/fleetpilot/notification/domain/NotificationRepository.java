package com.fleetpilot.notification.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {

    Optional<Notification> findByRequestId(String requestId);

    /**
     * Takes a queued notification for sending. Only one consumer wins: two copies of a message processed at the same
     * time never send the e-mail twice. A notification stuck in SENDING (consumer stopped) is taken again later.
     *
     * @return 1 when the caller took it, 0 otherwise
     */
    @Modifying
    @Query("update versioned Notification n set n.status = :sending, n.claimedAt = :now where n.id = :id "
            + "and (n.status = :queued or (n.status = :sending and n.claimedAt < :stale))")
    int claim(UUID id, Instant now, Instant stale, Notification.Status queued, Notification.Status sending);

    default int claim(UUID id, Instant now, Instant stale) {
        return claim(id, now, stale, Notification.Status.QUEUED, Notification.Status.SENDING);
    }

    /** Counts per status and template since a date, for the header of the journal. */
    @Query("select n.status, n.template, count(n) from Notification n where n.createdAt >= :since "
            + "group by n.status, n.template")
    List<Object[]> countSince(Instant since);
}

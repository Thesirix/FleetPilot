package com.fleetpilot.reporting.domain;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    Page<Report> findByRequestedByOrderByRequestedAtDesc(String requestedBy, Pageable pageable);

    Page<Report> findAllByOrderByRequestedAtDesc(Pageable pageable);
}

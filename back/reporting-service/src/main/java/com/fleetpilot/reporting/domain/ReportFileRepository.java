package com.fleetpilot.reporting.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportFileRepository extends JpaRepository<ReportFile, UUID> {
}

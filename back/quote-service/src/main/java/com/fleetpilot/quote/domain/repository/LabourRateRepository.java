package com.fleetpilot.quote.domain.repository;

import com.fleetpilot.quote.domain.model.LabourRate;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reference tariff table, small: read in full and cached by the tariff catalog. */
public interface LabourRateRepository extends JpaRepository<LabourRate, LabourRate.Key> {
}

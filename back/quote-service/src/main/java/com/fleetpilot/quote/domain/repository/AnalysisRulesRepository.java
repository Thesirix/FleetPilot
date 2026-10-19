package com.fleetpilot.quote.domain.repository;

import com.fleetpilot.quote.domain.model.AnalysisRules;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRulesRepository extends JpaRepository<AnalysisRules, Short> {
}

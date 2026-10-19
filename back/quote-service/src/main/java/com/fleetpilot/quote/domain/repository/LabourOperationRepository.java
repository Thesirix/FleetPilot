package com.fleetpilot.quote.domain.repository;

import java.util.List;

import com.fleetpilot.quote.domain.model.LabourOperation;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reference tariff table, small: read in full and cached by the tariff catalog. */
public interface LabourOperationRepository extends JpaRepository<LabourOperation, String> {

    List<LabourOperation> findAllByOrderByLabourCategoryAscCodeAsc();
}

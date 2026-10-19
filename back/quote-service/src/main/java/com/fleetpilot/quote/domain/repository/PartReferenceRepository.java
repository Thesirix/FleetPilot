package com.fleetpilot.quote.domain.repository;

import java.util.List;

import com.fleetpilot.quote.domain.model.PartReference;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reference tariff table, small: read in full and cached by the tariff catalog. */
public interface PartReferenceRepository extends JpaRepository<PartReference, String> {

    List<PartReference> findAllByOrderByFamilyAscCodeAsc();
}

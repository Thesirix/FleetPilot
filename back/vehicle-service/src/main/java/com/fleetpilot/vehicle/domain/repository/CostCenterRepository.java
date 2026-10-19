package com.fleetpilot.vehicle.domain.repository;

import java.util.List;
import java.util.UUID;

import com.fleetpilot.vehicle.domain.model.CostCenter;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CostCenterRepository extends JpaRepository<CostCenter, UUID> {

    List<CostCenter> findByCompanyIdOrderByCodeAsc(UUID companyId);
}

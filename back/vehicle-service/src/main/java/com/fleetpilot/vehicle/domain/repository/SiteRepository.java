package com.fleetpilot.vehicle.domain.repository;

import java.util.List;
import java.util.UUID;

import com.fleetpilot.vehicle.domain.model.Site;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteRepository extends JpaRepository<Site, UUID> {

    List<Site> findByCompanyIdOrderByNameAsc(UUID companyId);
}

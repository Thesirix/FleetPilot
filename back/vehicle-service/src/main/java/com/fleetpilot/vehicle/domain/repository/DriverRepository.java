package com.fleetpilot.vehicle.domain.repository;

import java.util.UUID;

import com.fleetpilot.vehicle.domain.model.Driver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DriverRepository extends JpaRepository<Driver, UUID>, JpaSpecificationExecutor<Driver> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    /** The underscore makes Spring Data follow the association instead of the {@code getCompanyId()} shortcut. */
    boolean existsByCompany_IdAndEmployeeNumber(UUID companyId, String employeeNumber);

    @Override
    @EntityGraph(attributePaths = "site")
    Page<Driver> findAll(Specification<Driver> spec, Pageable pageable);
}

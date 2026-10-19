package com.fleetpilot.vehicle.application;

import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.common.error.ResourceNotFoundException;

/**
 * Tells which client company the current caller may see. FleetPilot staff see every company; a client manager or a
 * driver only sees their own. Data of another company is reported as "not found", never as "forbidden", so its
 * existence is not disclosed.
 */
public interface TenantScope {

    /** The only company the caller may access, or empty when the caller sees every company. */
    Optional<UUID> restrictedCompanyId();

    /**
     * The leasing company a lessor user works for (extranet of the lessors): such a caller only sees the contracts of
     * this lessor and their vehicles. Empty for every other caller.
     */
    default Optional<String> restrictedLessor() {
        return Optional.empty();
    }

    /** Company filter to apply to a search: the caller's own company wins over the requested one. */
    default UUID companyFilter(UUID requested) {
        return restrictedCompanyId().orElse(requested);
    }

    /** Throws "not found" when the record belongs to a company the caller may not see. */
    default void checkAccess(UUID companyId, String resource, Object id) {
        restrictedCompanyId().ifPresent(allowed -> {
            if (!allowed.equals(companyId)) {
                throw new ResourceNotFoundException(resource, id);
            }
        });
    }
}

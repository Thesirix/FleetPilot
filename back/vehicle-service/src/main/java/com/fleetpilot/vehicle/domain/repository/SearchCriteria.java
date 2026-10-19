package com.fleetpilot.vehicle.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fleetpilot.vehicle.domain.model.DeadlineType;
import com.fleetpilot.vehicle.domain.model.Energy;
import com.fleetpilot.vehicle.domain.model.Incident;
import com.fleetpilot.vehicle.domain.model.LeaseContract;
import com.fleetpilot.vehicle.domain.model.Urgency;
import com.fleetpilot.vehicle.domain.model.VehicleCategory;
import com.fleetpilot.vehicle.domain.model.VehicleStatus;

/** Filters of the list screens. Every field is optional; null means "no filter". */
public final class SearchCriteria {

    private SearchCriteria() {
    }

    /**
     * @param text     free text matched against registration, VIN, make and model
     * @param assigned true: only vehicles with a current driver, false: only vehicles without
     * @param ids      only these vehicles (used to load the vehicles of a page of another list in one query)
     */
    public record VehicleCriteria(UUID companyId, UUID siteId, VehicleStatus status, VehicleCategory category,
            Energy energy, String text, Boolean assigned, List<UUID> ids) {

        public VehicleCriteria(UUID companyId, UUID siteId, VehicleStatus status, VehicleCategory category,
                Energy energy, String text, Boolean assigned) {
            this(companyId, siteId, status, category, energy, text, assigned, null);
        }

        public VehicleCriteria withCompany(UUID company) {
            return new VehicleCriteria(company, siteId, status, category, energy, text, assigned, ids);
        }
    }

    /**
     * @param text     free text matched against name, e-mail and employee number
     * @param assigned true: only drivers with a current vehicle, false: only drivers without
     */
    public record DriverCriteria(UUID companyId, UUID siteId, Boolean active, String text, Boolean assigned) {

        public DriverCriteria withCompany(UUID company) {
            return new DriverCriteria(company, siteId, active, text, assigned);
        }
    }

    public record ContractCriteria(UUID companyId, String lessor, LeaseContract.Status status, LocalDate endingBefore) {

        public ContractCriteria withCompany(UUID company) {
            return new ContractCriteria(company, lessor, status, endingBefore);
        }

        public ContractCriteria withLessor(String only) {
            return new ContractCriteria(companyId, only, status, endingBefore);
        }
    }

    /** @param urgency only open deadlines of this urgency, evaluated against {@code today} */
    public record DeadlineCriteria(UUID companyId, UUID vehicleId, DeadlineType type, Urgency urgency,
            LocalDate dueBefore, LocalDate today) {

        public DeadlineCriteria withCompany(UUID company) {
            return new DeadlineCriteria(company, vehicleId, type, urgency, dueBefore, today);
        }
    }

    public record IncidentCriteria(UUID companyId, UUID vehicleId, Incident.Status status, Incident.Type type,
            Boolean open) {

        public IncidentCriteria withCompany(UUID company) {
            return new IncidentCriteria(company, vehicleId, status, type, open);
        }
    }
}

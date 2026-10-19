package com.fleetpilot.vehicle.domain.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fleetpilot.vehicle.domain.model.Assignment;
import com.fleetpilot.vehicle.domain.model.Deadline;
import com.fleetpilot.vehicle.domain.model.Driver;
import com.fleetpilot.vehicle.domain.model.Incident;
import com.fleetpilot.vehicle.domain.model.LeaseContract;
import com.fleetpilot.vehicle.domain.model.Urgency;
import com.fleetpilot.vehicle.domain.model.Vehicle;
import com.fleetpilot.vehicle.domain.repository.SearchCriteria.ContractCriteria;
import com.fleetpilot.vehicle.domain.repository.SearchCriteria.DeadlineCriteria;
import com.fleetpilot.vehicle.domain.repository.SearchCriteria.DriverCriteria;
import com.fleetpilot.vehicle.domain.repository.SearchCriteria.IncidentCriteria;
import com.fleetpilot.vehicle.domain.repository.SearchCriteria.VehicleCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/** Translates the search criteria of the list screens into JPA criteria queries. */
public final class FleetSpecifications {

    /** Same thresholds as {@link Deadline#urgency}, expressed in SQL so that filtering and paging happen in the database. */
    private static final int DUE_SOON_DAYS = 30;
    private static final int DUE_SOON_KM = 1_500;

    private FleetSpecifications() {
    }

    public static Specification<Vehicle> vehicles(VehicleCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            addEqual(p, cb, root.get("company").get("id"), c.companyId());
            addEqual(p, cb, root.get("site").get("id"), c.siteId());
            addEqual(p, cb, root.get("status"), c.status());
            addEqual(p, cb, root.get("category"), c.category());
            addEqual(p, cb, root.get("energy"), c.energy());
            if (c.ids() != null && !c.ids().isEmpty()) {
                p.add(root.get("id").in(c.ids()));
            }
            if (hasText(c.text())) {
                String like = like(c.text());
                String compact = like(c.text().replaceAll("[\\s-]", ""));
                p.add(cb.or(
                        cb.like(cb.function("replace", String.class, root.get("registration"), cb.literal("-"),
                                cb.literal("")), compact.toUpperCase(Locale.ROOT)),
                        cb.like(root.get("vin"), compact.toUpperCase(Locale.ROOT)),
                        cb.like(cb.lower(root.get("make")), like),
                        cb.like(cb.lower(root.get("model")), like)));
            }
            if (c.assigned() != null) {
                Subquery<Integer> current = query.subquery(Integer.class);
                Root<Assignment> a = current.from(Assignment.class);
                current.select(cb.literal(1))
                        .where(cb.equal(a.get("vehicle"), root), cb.isNull(a.get("endDate")));
                p.add(c.assigned() ? cb.exists(current) : cb.not(cb.exists(current)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    public static Specification<Driver> drivers(DriverCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            addEqual(p, cb, root.get("company").get("id"), c.companyId());
            addEqual(p, cb, root.get("site").get("id"), c.siteId());
            addEqual(p, cb, root.get("active"), c.active());
            if (hasText(c.text())) {
                String like = like(c.text());
                Expression<String> fullName = cb.lower(cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName")));
                p.add(cb.or(
                        cb.like(fullName, like),
                        cb.like(cb.lower(root.get("lastName")), like),
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("employeeNumber")), like)));
            }
            if (c.assigned() != null) {
                Subquery<Integer> current = query.subquery(Integer.class);
                Root<Assignment> a = current.from(Assignment.class);
                current.select(cb.literal(1))
                        .where(cb.equal(a.get("driver"), root), cb.isNull(a.get("endDate")));
                p.add(c.assigned() ? cb.exists(current) : cb.not(cb.exists(current)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    public static Specification<LeaseContract> contracts(ContractCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            addEqual(p, cb, root.get("vehicle").get("company").get("id"), c.companyId());
            addEqual(p, cb, root.get("lessor"), c.lessor());
            addEqual(p, cb, root.get("status"), c.status());
            if (c.endingBefore() != null) {
                p.add(cb.lessThanOrEqualTo(root.get("endDate"), c.endingBefore()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    public static Specification<Deadline> deadlines(DeadlineCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            Path<Vehicle> vehicle = root.get("vehicle");
            addEqual(p, cb, vehicle.get("company").get("id"), c.companyId());
            addEqual(p, cb, vehicle.get("id"), c.vehicleId());
            addEqual(p, cb, root.get("type"), c.type());
            p.add(cb.equal(root.get("status"), Deadline.Status.OPEN));
            if (c.dueBefore() != null) {
                p.add(cb.lessThanOrEqualTo(root.get("dueDate"), c.dueBefore()));
            }
            if (c.urgency() != null) {
                p.add(urgency(cb, root, vehicle, c.urgency(), c.today()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    public static Specification<Incident> incidents(IncidentCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            addEqual(p, cb, root.get("vehicle").get("company").get("id"), c.companyId());
            addEqual(p, cb, root.get("vehicle").get("id"), c.vehicleId());
            addEqual(p, cb, root.get("status"), c.status());
            addEqual(p, cb, root.get("type"), c.type());
            if (c.open() != null) {
                Predicate closed = root.get("status").in(Incident.Status.CLOSED, Incident.Status.REJECTED);
                p.add(c.open() ? cb.not(closed) : closed);
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private static Predicate urgency(CriteriaBuilder cb, Root<Deadline> root, Path<Vehicle> vehicle, Urgency urgency,
            LocalDate today) {
        Path<LocalDate> dueDate = root.get("dueDate");
        Path<Integer> dueMileage = root.get("dueMileage");
        Path<Integer> mileage = vehicle.get("mileage");
        Predicate overdue = cb.or(
                cb.and(cb.isNotNull(dueDate), cb.lessThan(dueDate, today)),
                cb.and(cb.isNotNull(dueMileage), cb.greaterThanOrEqualTo(mileage, dueMileage)));
        Predicate soon = cb.or(
                cb.and(cb.isNotNull(dueDate), cb.lessThanOrEqualTo(dueDate, today.plusDays(DUE_SOON_DAYS))),
                cb.and(cb.isNotNull(dueMileage), cb.lessThanOrEqualTo(cb.diff(dueMileage, mileage), DUE_SOON_KM)));
        return switch (urgency) {
            case OVERDUE -> overdue;
            case DUE_SOON -> cb.and(cb.not(overdue), soon);
            case UPCOMING -> cb.and(cb.not(overdue), cb.not(soon));
        };
    }

    private static <T> void addEqual(List<Predicate> predicates, CriteriaBuilder cb, Path<T> path, T value) {
        if (value != null) {
            predicates.add(cb.equal(path, value));
        }
    }

    private static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private static String like(String text) {
        String escaped = text.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}

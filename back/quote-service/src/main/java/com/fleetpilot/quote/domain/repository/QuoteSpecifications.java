package com.fleetpilot.quote.domain.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.fleetpilot.quote.domain.model.Garage;
import com.fleetpilot.quote.domain.model.GarageNetwork;
import com.fleetpilot.quote.domain.model.Quote;
import com.fleetpilot.quote.domain.model.QuoteStatus;
import com.fleetpilot.quote.domain.model.Region;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

/** Filters of the quote and garage lists, translated into JPA criteria queries. Every field is optional. */
public final class QuoteSpecifications {

    private QuoteSpecifications() {
    }

    /**
     * @param open         true: received, in review or to negotiate; false: decided
     * @param withFindings true: only quotes with at least one finding
     * @param text         reference of the quote, of the garage, or registration (any spelling)
     */
    public record QuoteCriteria(UUID companyId, UUID garageId, UUID vehicleId, QuoteStatus status, Boolean open,
            Boolean withFindings, String text, Instant submittedFrom, Instant submittedTo) {

        public QuoteCriteria scoped(UUID company, UUID garage) {
            return new QuoteCriteria(company, garage, vehicleId, status, open, withFindings, text, submittedFrom,
                    submittedTo);
        }
    }

    public record GarageCriteria(Region region, GarageNetwork network, Boolean approved, String text) {
    }

    public static Specification<Quote> quotes(QuoteCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            addEqual(p, cb, root.get("companyId"), c.companyId());
            // associations are only navigated when filtered on, so that no join drops the quotes without vehicle
            if (c.garageId() != null) {
                p.add(cb.equal(root.get("garage").get("id"), c.garageId()));
            }
            if (c.vehicleId() != null) {
                p.add(cb.equal(root.get("vehicle").get("id"), c.vehicleId()));
            }
            addEqual(p, cb, root.get("status"), c.status());
            if (c.open() != null) {
                Predicate open = root.get("status").in(QuoteStatus.RECEIVED, QuoteStatus.IN_REVIEW, QuoteStatus.TO_NEGOTIATE);
                p.add(c.open() ? open : cb.not(open));
            }
            if (c.withFindings() != null) {
                p.add(c.withFindings() ? cb.greaterThan(root.get("findingCount"), 0)
                        : cb.equal(root.get("findingCount"), 0));
            }
            if (c.submittedFrom() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("submittedAt"), c.submittedFrom()));
            }
            if (c.submittedTo() != null) {
                p.add(cb.lessThan(root.get("submittedAt"), c.submittedTo()));
            }
            if (hasText(c.text())) {
                String compact = "%" + c.text().trim().replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT) + "%";
                String like = "%" + c.text().trim().toUpperCase(Locale.ROOT) + "%";
                p.add(cb.or(
                        cb.like(cb.function("replace", String.class, root.get("registration"), cb.literal("-"),
                                cb.literal("")), compact),
                        cb.like(root.get("reference"), like),
                        cb.like(cb.upper(cb.coalesce(root.get("garageReference"), "")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    public static Specification<Garage> garages(GarageCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            addEqual(p, cb, root.get("region"), c.region());
            addEqual(p, cb, root.get("network"), c.network());
            addEqual(p, cb, root.get("approved"), c.approved());
            if (hasText(c.text())) {
                String like = "%" + c.text().trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("city")), like),
                        cb.like(cb.lower(root.get("code")), like),
                        cb.like(root.get("postalCode"), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private static <T> void addEqual(List<Predicate> predicates, CriteriaBuilder cb, Path<T> path, T value) {
        if (value != null) {
            predicates.add(cb.equal(path, value));
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

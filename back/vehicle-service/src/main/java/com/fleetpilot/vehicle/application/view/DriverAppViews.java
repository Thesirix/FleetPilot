package com.fleetpilot.vehicle.application.view;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fleetpilot.vehicle.application.view.Views.DriverRef;
import com.fleetpilot.vehicle.domain.model.FuelEntry;
import com.fleetpilot.vehicle.domain.model.Inspection;
import com.fleetpilot.vehicle.domain.repository.PhotoRepository.PhotoSummary;
import io.swagger.v3.oas.annotations.media.Schema;

/** Read models of what the drivers send from the mobile app, for the driver and for the fleet managers. */
public final class DriverAppViews {

    private DriverAppViews() {
    }

    public record FuelEntryView(UUID id, UUID vehicleId, DriverRef driver, Instant filledAt, FuelEntry.Kind kind,
            BigDecimal quantity, BigDecimal amount,
            @Schema(description = "Price per litre or per kWh") BigDecimal unitPrice, int mileage, String station,
            FuelEntry.Payment payment, Instant createdAt) {

        public static FuelEntryView of(FuelEntry e, DriverRef driver) {
            return new FuelEntryView(e.getId(), e.getVehicleId(), driver, e.getFilledAt(), e.getKind(), e.getQuantity(),
                    e.getAmount(), e.getAmount().divide(e.getQuantity(), 3, RoundingMode.HALF_UP), e.getMileage(),
                    e.getStation(), e.getPayment(), e.getCreatedAt());
        }
    }

    public record CheckView(Inspection.Item item, Inspection.Condition condition, String comment) {
    }

    public record PhotoView(UUID id, String contentType, int sizeBytes, Instant createdAt) {

        public static PhotoView of(PhotoSummary p) {
            return new PhotoView(p.getId(), p.getContentType(), p.getSizeBytes(), p.getCreatedAt());
        }
    }

    public record InspectionView(UUID id, UUID vehicleId, DriverRef driver, Inspection.Kind kind, Instant performedAt,
            int mileage, int fuelLevel, String remarks, List<CheckView> checks,
            @Schema(description = "Points found damaged or missing") int defects, List<PhotoView> photos,
            Instant createdAt) {

        public static InspectionView of(Inspection i, DriverRef driver, List<PhotoView> photos) {
            List<CheckView> checks = i.getChecks().stream()
                    .map(c -> new CheckView(c.getItem(), c.getCondition(), c.getComment()))
                    .toList();
            return new InspectionView(i.getId(), i.getVehicleId(), driver, i.getKind(), i.getPerformedAt(),
                    i.getMileage(), i.getFuelLevel(), i.getRemarks(), checks, i.defects().size(), photos,
                    i.getCreatedAt());
        }
    }

    /**
     * Something the driver should know, shown in the app and turned into a phone notification the first time it is
     * seen. The id is stable while nothing changes, and changes when the situation gets worse (a deadline that becomes
     * overdue, a claim that moves to another status), so the app notifies again only then.
     */
    public record DriverNotification(String id, Kind kind, Level level,
            @Schema(description = "Deadline type, alert type or claim status") String code,
            @Schema(description = "Text of the alert, or reference of the claim") String detail,
            @Schema(description = "Due date of a deadline, when it has one") LocalDate dueDate,
            Integer dueMileage, Instant at) {

        public enum Kind {
            DEADLINE,
            VEHICLE_ALERT,
            INCIDENT
        }

        public enum Level {
            INFO,
            WARNING,
            CRITICAL
        }
    }
}

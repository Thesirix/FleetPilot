package com.fleetpilot.vehicle.application.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fleetpilot.vehicle.domain.model.Incident;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Requests about contracts, deadlines and incidents. */
public final class FleetCommands {

    private FleetCommands() {
    }

    @Schema(description = "A long-term lease signed for a vehicle")
    public record CreateContract(
            @NotBlank @Size(max = 40) @Schema(example = "Ayvens") String lessor,
            @NotBlank @Size(max = 30) String contractNumber,
            @NotNull LocalDate startDate,
            @NotNull @Min(6) @Max(84) Integer durationMonths,
            @NotNull @Positive Integer contractualMileage,
            @NotNull @DecimalMin("1.00") @Digits(integer = 8, fraction = 2) BigDecimal monthlyRent,
            @Schema(description = "Defaults to false") Boolean maintenanceIncluded,
            @Schema(description = "Defaults to false") Boolean tyresIncluded,
            @Schema(description = "Defaults to false") Boolean replacementVehicle) {
    }

    public record EndContract(@Schema(description = "Defaults to today") LocalDate endedOn) {
    }

    @Schema(description = "The obligation was met (inspection passed, service done...)")
    public record CompleteDeadline(
            @Schema(description = "Defaults to today") @PastOrPresent LocalDate completedOn,
            @Schema(description = "Defaults to the vehicle's current mileage") @PositiveOrZero Integer mileage) {
    }

    @Schema(description = "Declaration of a claim")
    public record DeclareIncident(
            UUID driverId,
            @NotNull Incident.Type type,
            @NotNull @PastOrPresent Instant occurredAt,
            @NotBlank @Size(max = 200) String location,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @NotBlank @Size(max = 2000) String description,
            @Schema(description = "Defaults to false") Boolean thirdPartyInvolved,
            @Schema(description = "Defaults to false") Boolean injuries) {
    }

    @Schema(description = "Assessment of a claim by the fleet manager or the insurer")
    public record AssessIncident(
            @NotNull Incident.Responsibility responsibility,
            @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal estimatedCost) {
    }

    public record ChangeIncidentStatus(@NotNull Incident.Status status) {
    }
}

package com.fleetpilot.vehicle.application.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fleetpilot.vehicle.domain.model.FuelEntry;
import com.fleetpilot.vehicle.domain.model.Inspection;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** What a driver sends from the mobile app. */
public final class DriverAppCommands {

    private DriverAppCommands() {
    }

    @Schema(description = "A fill-up or a charge, typed from the receipt")
    public record RecordFuel(
            @NotNull Instant filledAt,
            @NotNull FuelEntry.Kind kind,
            @NotNull @Positive @Digits(integer = 5, fraction = 2) @Schema(description = "Litres, or kWh for a charge",
                    example = "42.17") BigDecimal quantity,
            @NotNull @PositiveOrZero @Digits(integer = 6, fraction = 2) @Schema(example = "76.33") BigDecimal amount,
            @NotNull @PositiveOrZero Integer mileage,
            @Size(max = 120) @Schema(example = "TotalEnergies Lyon Gerland") String station,
            @NotNull FuelEntry.Payment payment) {
    }

    @Schema(description = "Check of the vehicle: every point of the checklist is answered")
    public record RecordInspection(
            @NotNull Inspection.Kind kind,
            @NotNull Instant performedAt,
            @NotNull @PositiveOrZero Integer mileage,
            @NotNull @Min(0) @Max(100) @Schema(description = "Fuel or battery level, in percent") Integer fuelLevel,
            @Size(max = 1000) String remarks,
            @NotEmpty @Size(max = 20) List<@Valid CheckAnswer> checks) {
    }

    public record CheckAnswer(
            @NotNull Inspection.Item item,
            @NotNull Inspection.Condition condition,
            @Size(max = 300) @Schema(description = "Required when the point is damaged or missing") String comment) {
    }
}

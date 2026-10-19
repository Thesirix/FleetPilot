package com.fleetpilot.vehicle.application.command;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fleetpilot.common.validation.RegistrationNumber;
import com.fleetpilot.common.validation.Vin;
import com.fleetpilot.vehicle.domain.model.Acquisition;
import com.fleetpilot.vehicle.domain.model.Energy;
import com.fleetpilot.vehicle.domain.model.MileageSource;
import com.fleetpilot.vehicle.domain.model.VehicleCategory;
import com.fleetpilot.vehicle.domain.model.VehicleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Requests that change vehicles. */
public final class VehicleCommands {

    private VehicleCommands() {
    }

    @Schema(description = "A vehicle entering the fleet")
    public record CreateVehicle(
            @NotNull UUID companyId,
            UUID siteId,
            UUID costCenterId,
            @NotBlank @RegistrationNumber @Schema(example = "GH-482-KM") String registration,
            @NotBlank @Vin @Schema(example = "VF1RJA00X68123456") String vin,
            @NotNull VehicleCategory category,
            @NotBlank @Size(max = 40) @Schema(example = "Renault") String make,
            @NotBlank @Size(max = 60) @Schema(example = "Megane E-Tech") String model,
            @Size(max = 80) String trimLevel,
            @NotNull Energy energy,
            @NotNull @Min(0) @Max(5) @Schema(description = "0 is the Crit'Air E sticker") Integer critAir,
            @NotNull @PositiveOrZero Integer co2GramsPerKm,
            @Min(1) @Max(99) Integer fiscalPower,
            @NotNull @PastOrPresent LocalDate firstRegistrationDate,
            @NotNull Acquisition acquisition,
            @NotNull @PositiveOrZero Integer mileage,
            @Schema(description = "Defaults to ACTIVE") VehicleStatus status) {
    }

    @Schema(description = "Editable data of a vehicle. The company, VIN and mileage cannot be changed here.")
    public record UpdateVehicle(
            UUID siteId,
            UUID costCenterId,
            @NotBlank @RegistrationNumber String registration,
            @NotNull VehicleCategory category,
            @NotBlank @Size(max = 40) String make,
            @NotBlank @Size(max = 60) String model,
            @Size(max = 80) String trimLevel,
            @NotNull Energy energy,
            @NotNull @Min(0) @Max(5) Integer critAir,
            @NotNull @PositiveOrZero Integer co2GramsPerKm,
            @Min(1) @Max(99) Integer fiscalPower,
            @NotNull @PastOrPresent LocalDate firstRegistrationDate,
            @NotNull @Schema(description = "Version read by the client, for optimistic locking") Long version) {
    }

    public record ChangeStatus(@NotNull VehicleStatus status) {
    }

    public record RecordMileage(
            @NotNull @PositiveOrZero Integer mileage,
            @Schema(description = "Defaults to now") @PastOrPresent Instant readAt,
            @Schema(description = "Defaults to MANAGER") MileageSource source) {
    }
}

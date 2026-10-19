package com.fleetpilot.vehicle.application.command;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import com.fleetpilot.vehicle.domain.model.Assignment;
import com.fleetpilot.vehicle.domain.model.LicenseCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Requests that change drivers and assignments. */
public final class DriverCommands {

    private static final String PHONE = "^\\+?[0-9 .]{10,20}$";

    private DriverCommands() {
    }

    public record CreateDriver(
            @NotNull UUID companyId,
            UUID siteId,
            @NotBlank @Size(max = 20) @Schema(example = "NEO-00412") String employeeNumber,
            @NotBlank @Size(max = 60) String firstName,
            @NotBlank @Size(max = 60) String lastName,
            @NotBlank @Email @Size(max = 160) String email,
            @Pattern(regexp = PHONE) String phone,
            @NotBlank @Size(max = 20) String licenseNumber,
            @NotEmpty Set<LicenseCategory> licenseCategories,
            @NotNull @PastOrPresent LocalDate licenseIssuedOn,
            LocalDate licenseExpiresOn) {
    }

    public record UpdateDriver(
            UUID siteId,
            @NotBlank @Size(max = 20) String employeeNumber,
            @NotBlank @Size(max = 60) String firstName,
            @NotBlank @Size(max = 60) String lastName,
            @NotBlank @Email @Size(max = 160) String email,
            @Pattern(regexp = PHONE) String phone,
            @NotBlank @Size(max = 20) String licenseNumber,
            @NotEmpty Set<LicenseCategory> licenseCategories,
            @NotNull @PastOrPresent LocalDate licenseIssuedOn,
            LocalDate licenseExpiresOn,
            @NotNull Boolean active,
            @NotNull Long version) {
    }

    @Schema(description = "Hand a vehicle over to a driver")
    public record Assign(
            @NotNull UUID driverId,
            @Schema(description = "Defaults to PERMANENT") Assignment.Kind kind,
            @Schema(description = "Defaults to today") LocalDate startDate) {
    }

    @Schema(description = "The driver returns the vehicle")
    public record EndAssignment(
            @Schema(description = "Defaults to today") LocalDate endDate,
            @Schema(description = "Odometer at return, defaults to the vehicle's current mileage")
            @PositiveOrZero Integer endMileage) {
    }
}

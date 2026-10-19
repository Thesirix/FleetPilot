package com.fleetpilot.vehicle.application.command;

import java.math.BigDecimal;

import com.fleetpilot.vehicle.domain.model.CompanySegment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Requests of the administration: client companies, their sites and their cost centers. */
public final class AdminCommands {

    private AdminCommands() {
    }

    @Schema(description = "A client company whose fleet FleetPilot manages")
    public record SaveCompany(
            @NotBlank @Size(max = 120) @Schema(example = "Néolia Énergies") String name,
            @NotBlank @Size(max = 160) @Schema(example = "Néolia Énergies SA") String legalName,
            @NotBlank @Pattern(regexp = "\\d{9}") @Schema(example = "552081317") String siren,
            @NotNull CompanySegment segment,
            @Size(max = 200) String address,
            @Pattern(regexp = "\\d{5}") String postalCode,
            @NotBlank @Size(max = 80) String city,
            @Size(max = 120) String contactName,
            @Email @Size(max = 160) String contactEmail,
            @Size(max = 30) String contactPhone,
            @Schema(description = "Version read before editing; omitted at creation") Long version) {
    }

    @Schema(description = "A depot or office of a client company")
    public record SaveSite(
            @NotBlank @Size(max = 120) @Schema(example = "Marseille - Arenc") String name,
            @NotBlank @Size(max = 200) String address,
            @NotBlank @Pattern(regexp = "\\d{5}") String postalCode,
            @NotBlank @Size(max = 80) String city,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            Long version) {
    }

    @Schema(description = "A budget line of a client company")
    public record SaveCostCenter(
            @NotBlank @Size(max = 20) @Schema(example = "NEO-DIR") String code,
            @NotBlank @Size(max = 120) @Schema(example = "Direction générale") String label,
            Long version) {
    }
}

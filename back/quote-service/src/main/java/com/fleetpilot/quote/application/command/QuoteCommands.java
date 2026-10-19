package com.fleetpilot.quote.application.command;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fleetpilot.common.validation.RegistrationNumber;
import com.fleetpilot.quote.domain.analysis.QuoteLineInput;
import com.fleetpilot.quote.domain.model.GarageNetwork;
import com.fleetpilot.quote.domain.model.LineKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Request bodies of the quote API. Wrapper types: a missing value is a validation error, not a silent zero. */
public final class QuoteCommands {

    private QuoteCommands() {
    }

    public record Line(
            @NotNull LineKind kind,
            @Size(max = 30) String code,
            @NotBlank @Size(max = 200) String label,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 6, fraction = 2) BigDecimal quantity,
            @NotNull @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) BigDecimal unitPrice) {

        public QuoteLineInput toInput() {
            return new QuoteLineInput(kind, code, label, quantity, unitPrice);
        }
    }

    /**
     * A quote sent by a garage. Staff may submit on behalf of a garage, named by its id or by its SIRET (the integration
     * bus only knows what the garage's file says); a garage always submits for itself, whatever the body says.
     */
    public record SubmitQuote(
            UUID garageId,
            @Pattern(regexp = "\\d{14}", message = "must be 14 digits") String garageSiret,
            @NotBlank @RegistrationNumber String registration,
            @Size(max = 40) String garageReference,
            @PositiveOrZero Integer mileage,
            @NotBlank @Size(max = 1000) String description,
            @NotEmpty @Size(max = 60) List<@Valid Line> lines) {

        public List<QuoteLineInput> lineInputs() {
            return lines.stream().map(Line::toInput).toList();
        }
    }

    public record ReviseQuote(@NotEmpty @Size(max = 60) List<@Valid Line> lines, @Size(max = 1000) String message) {

        public List<QuoteLineInput> lineInputs() {
            return lines.stream().map(Line::toInput).toList();
        }
    }

    public record IdentifyVehicle(@NotBlank @RegistrationNumber String registration) {
    }

    public record CounterOffer(
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @Size(max = 1000) String message) {
    }

    public record AnswerCounterOffer(@NotNull Boolean accept, @Size(max = 1000) String message) {
    }

    public record Decision(@Size(max = 1000) String comment) {
    }

    public record Rejection(@NotBlank @Size(max = 1000) String reason) {
    }

    public record Completion(LocalDate completedOn) {
    }

    public record InvoiceQuote(
            @NotBlank @Size(max = 40) String number,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            LocalDate invoicedOn) {
    }

    // ---------------------------------------------------------------- garages and tariff

    public record SaveGarage(
            @NotBlank @Size(max = 120) String name,
            @NotNull GarageNetwork network,
            @Size(max = 40) String brand,
            @NotBlank @Pattern(regexp = "\\d{14}", message = "must be 14 digits") String siret,
            @NotBlank @Size(max = 160) String address,
            @NotBlank @Pattern(regexp = "\\d{5}", message = "must be a French postal code") String postalCode,
            @NotBlank @Size(max = 80) String city,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @NotBlank @Email @Size(max = 160) String email,
            @Size(max = 30) String phone,
            @NotNull @DecimalMin(value = "1.00") @Digits(integer = 4, fraction = 2) BigDecimal hourlyRate) {
    }

    public record Approval(@NotNull Boolean approved, @DecimalMin("1.0") @DecimalMax("5.0") BigDecimal rating) {
    }

    public record UpdateRate(
            @NotNull @DecimalMin(value = "1.00") @Digits(integer = 4, fraction = 2) BigDecimal hourlyRate,
            @NotNull @DecimalMin(value = "1.00") @Digits(integer = 4, fraction = 2) BigDecimal truckHourlyRate) {
    }
}

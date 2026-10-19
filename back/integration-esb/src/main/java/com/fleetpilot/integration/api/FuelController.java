package com.fleetpilot.integration.api;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import com.fleetpilot.common.dto.PageResponse;
import com.fleetpilot.common.security.CurrentUser;
import com.fleetpilot.common.security.Roles;
import com.fleetpilot.integration.route.fuelcard.FuelTransactions;
import com.fleetpilot.integration.route.fuelcard.FuelTransactions.FuelTransaction;
import com.fleetpilot.integration.route.fuelcard.FuelTransactions.Summary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fuel card transactions received from the providers, with the anomalies found. A client manager sees the
 * transactions of their own company only.
 */
@RestController
@RequestMapping("/api/v1/fuel")
@PreAuthorize(Roles.ANY_MANAGER)
@Tag(name = "Fuel cards", description = "Transactions of the fuel cards and their anomalies")
public class FuelController {

    private static final UUID NO_COMPANY = new UUID(0, 0);

    private final FuelTransactions transactions;
    private final Clock clock;

    public FuelController(FuelTransactions transactions, Clock clock) {
        this.transactions = transactions;
        this.clock = clock;
    }

    @GetMapping("/transactions")
    @Operation(summary = "Fuel card transactions", description = "Most recent first; anomaly: WRONG_FUEL, "
            + "OVER_CAPACITY, TOO_FREQUENT, ODOMETER, UNKNOWN_VEHICLE")
    public PageResponse<FuelTransaction> search(@RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) UUID vehicleId, @RequestParam(required = false) String anomaly,
            @RequestParam(defaultValue = "false") boolean onlyAnomalies, @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(size = 25) Pageable pageable) {
        return PageResponse.of(transactions.search(scope(companyId), vehicleId, anomaly, onlyAnomalies, q, pageable));
    }

    @GetMapping("/summary")
    @Operation(summary = "Fuel spending and anomalies", description = "Over the last three months by default")
    public Summary summary(@RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        return transactions.summary(scope(companyId), from == null ? end.minusMonths(3).withDayOfMonth(1) : from, end);
    }

    /** FleetPilot staff see every company; a client manager only theirs, whatever they ask. */
    private static UUID scope(UUID requested) {
        if (CurrentUser.hasAnyRole(Roles.PLATFORM_ADMIN, Roles.FLEET_MANAGER)) {
            return requested;
        }
        return CurrentUser.companyId().orElse(NO_COMPANY);
    }
}

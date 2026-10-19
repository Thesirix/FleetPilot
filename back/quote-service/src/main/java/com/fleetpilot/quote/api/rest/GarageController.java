package com.fleetpilot.quote.api.rest;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.fleetpilot.common.dto.PageResponse;
import com.fleetpilot.common.error.ResourceNotFoundException;
import com.fleetpilot.common.security.CurrentUser;
import com.fleetpilot.common.security.Roles;
import com.fleetpilot.quote.application.GarageService;
import com.fleetpilot.quote.application.command.QuoteCommands.Approval;
import com.fleetpilot.quote.application.command.QuoteCommands.SaveGarage;
import com.fleetpilot.quote.application.view.QuoteViews.GarageDetail;
import com.fleetpilot.quote.application.view.QuoteViews.GarageSummary;
import com.fleetpilot.quote.application.view.QuoteViews.NearbyGarage;
import com.fleetpilot.quote.domain.model.GarageNetwork;
import com.fleetpilot.quote.domain.model.Region;
import com.fleetpilot.quote.domain.repository.QuoteSpecifications.GarageCriteria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/garages")
@Tag(name = "Garages", description = "Garage network: workshops, approval, track record")
public class GarageController {

    private final GarageService garages;

    public GarageController(GarageService garages) {
        this.garages = garages;
    }

    /** Every signed-in user: drivers look for an approved garage near them in the mobile app. */
    @GetMapping
    @Operation(summary = "Search the garage network", description = "Sort by name, city, hourlyRate, rating...")
    public PageResponse<GarageSummary> search(
            @RequestParam(required = false) Region region,
            @RequestParam(required = false) GarageNetwork network,
            @RequestParam(required = false) Boolean approved,
            @Parameter(description = "Name, city, code or postal code") @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return PageResponse.of(garages.search(new GarageCriteria(region, network, approved, q), pageable));
    }

    @GetMapping("/nearby")
    @Operation(summary = "Approved garages near a point", description = "Nearest first, as the crow flies; the mobile "
            + "app sends the position of the phone")
    public List<NearbyGarage> nearby(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double latitude,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double longitude,
            @Parameter(description = "Radius in kilometres") @RequestParam(defaultValue = "25") @Min(1) @Max(200)
            int radiusKm,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return garages.nearby(latitude, longitude, radiusKm, limit);
    }

    @GetMapping("/me")
    @PreAuthorize(Roles.GARAGE_ONLY)
    @Operation(summary = "The garage of the signed-in garage user")
    public GarageDetail mine() {
        UUID own = CurrentUser.garageId().orElseThrow(() -> new ResourceNotFoundException("Garage", "of this user"));
        return garages.get(own);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.ANY_MANAGER_OR_GARAGE)
    @Operation(summary = "Garage file", description = "With its track record: quotes, overcharge rate, savings, "
            + "decision time, disputed invoices")
    public GarageDetail get(@PathVariable UUID id) {
        return garages.get(id);
    }

    @PostMapping
    @PreAuthorize(Roles.ANY_STAFF)
    @Operation(summary = "Add a garage to the network")
    public ResponseEntity<GarageDetail> create(@Valid @RequestBody SaveGarage command) {
        GarageDetail created = garages.create(command);
        return ResponseEntity.created(URI.create("/api/v1/garages/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.ANY_STAFF)
    @Operation(summary = "Update a garage")
    public GarageDetail update(@PathVariable UUID id, @Valid @RequestBody SaveGarage command) {
        return garages.update(id, command);
    }

    @PutMapping("/{id}/approval")
    @PreAuthorize(Roles.ANY_STAFF)
    @Operation(summary = "Grant or withdraw the approval, rate the quality of the garage")
    public GarageDetail approve(@PathVariable UUID id, @Valid @RequestBody Approval command) {
        return garages.approve(id, command);
    }
}

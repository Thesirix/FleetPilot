package com.fleetpilot.vehicle.api.rest;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.common.security.CurrentUser;
import com.fleetpilot.common.security.Roles;
import com.fleetpilot.vehicle.application.DriverAppService;
import com.fleetpilot.vehicle.application.view.DriverAppViews.FuelEntryView;
import com.fleetpilot.vehicle.application.view.DriverAppViews.InspectionView;
import com.fleetpilot.vehicle.application.view.DriverAppViews.PhotoView;
import com.fleetpilot.vehicle.domain.model.Photo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What the drivers sent from the app, as the fleet managers see it on the vehicle and the claim. */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize(Roles.ANY_MANAGER)
@Tag(name = "Driver records", description = "Fill-ups, inspections and photos sent by the drivers")
public class DriverRecordsController {

    private final DriverAppService app;

    public DriverRecordsController(DriverAppService app) {
        this.app = app;
    }

    @GetMapping("/vehicles/{id}/fuel-entries")
    @Operation(summary = "Fill-ups and charges entered by the drivers of a vehicle", description = "Last 50")
    public List<FuelEntryView> fuelEntries(@PathVariable UUID id) {
        return app.fuelEntriesOfVehicle(id);
    }

    @GetMapping("/vehicles/{id}/inspections")
    @Operation(summary = "Inspections of a vehicle by its drivers", description = "Last 20, with their photos")
    public List<InspectionView> inspections(@PathVariable UUID id) {
        return app.inspectionsOfVehicle(id);
    }

    @GetMapping("/incidents/{id}/photos")
    @Operation(summary = "Photos of a claim")
    public List<PhotoView> incidentPhotos(@PathVariable UUID id) {
        return app.incidentPhotos(id);
    }

    @GetMapping("/photos/{id}")
    @PreAuthorize(Roles.ANY_MANAGER_OR_DRIVER)
    @Operation(summary = "A photo", description = "A driver only opens the photos of their own claims and inspections")
    public ResponseEntity<byte[]> photo(@PathVariable UUID id) {
        Optional<UUID> asDriver = CurrentUser.hasAnyRole(Roles.DRIVER)
                ? Optional.of(CurrentUser.driverId()
                        .orElseThrow(() -> new AccessDeniedException("The account is not linked to a driver")))
                : Optional.empty();
        Photo photo = app.photo(id, asDriver);
        // A photo never changes once sent: the browser may keep it, but only for this user.
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
                .body(photo.getContent());
    }
}

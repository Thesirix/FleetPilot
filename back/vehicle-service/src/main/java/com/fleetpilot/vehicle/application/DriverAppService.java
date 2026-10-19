package com.fleetpilot.vehicle.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fleetpilot.common.error.BusinessRuleException;
import com.fleetpilot.common.error.ResourceNotFoundException;
import com.fleetpilot.vehicle.application.command.DriverAppCommands.RecordFuel;
import com.fleetpilot.vehicle.application.command.DriverAppCommands.RecordInspection;
import com.fleetpilot.vehicle.application.view.DriverAppViews.DriverNotification;
import com.fleetpilot.vehicle.application.view.DriverAppViews.DriverNotification.Kind;
import com.fleetpilot.vehicle.application.view.DriverAppViews.DriverNotification.Level;
import com.fleetpilot.vehicle.application.view.DriverAppViews.FuelEntryView;
import com.fleetpilot.vehicle.application.view.DriverAppViews.InspectionView;
import com.fleetpilot.vehicle.application.view.DriverAppViews.PhotoView;
import com.fleetpilot.vehicle.application.view.Views.DriverRef;
import com.fleetpilot.vehicle.domain.model.Deadline;
import com.fleetpilot.vehicle.domain.model.Driver;
import com.fleetpilot.vehicle.domain.model.FuelEntry;
import com.fleetpilot.vehicle.domain.model.Incident;
import com.fleetpilot.vehicle.domain.model.Inspection;
import com.fleetpilot.vehicle.domain.model.Photo;
import com.fleetpilot.vehicle.domain.model.Urgency;
import com.fleetpilot.vehicle.domain.model.Vehicle;
import com.fleetpilot.vehicle.domain.model.VehicleAlert;
import com.fleetpilot.vehicle.domain.repository.DeadlineRepository;
import com.fleetpilot.vehicle.domain.repository.DriverRepository;
import com.fleetpilot.vehicle.domain.repository.FuelEntryRepository;
import com.fleetpilot.vehicle.domain.repository.IncidentRepository;
import com.fleetpilot.vehicle.domain.repository.InspectionRepository;
import com.fleetpilot.vehicle.domain.repository.PhotoRepository;
import com.fleetpilot.vehicle.domain.repository.VehicleAlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the drivers send from the mobile app (fill-ups, inspections, photos) and what the app shows them (their
 * notifications), plus the read side for the fleet managers.
 *
 * <p>The app works offline: it queues what the driver enters and sends it when the network is back, possibly twice
 * if the first answer was lost. Every record therefore carries an id generated on the phone; sending it again returns
 * the record already stored instead of creating a second one.
 */
@Service
@Transactional
public class DriverAppService {

    /** Alerts and claim updates older than this are no longer news. */
    static final Duration NOTIFICATION_WINDOW = Duration.ofDays(30);

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private final DriverSpaceService space;
    private final VehicleService vehicles;
    private final FuelEntryRepository fuelEntries;
    private final InspectionRepository inspections;
    private final PhotoRepository photos;
    private final IncidentRepository incidents;
    private final DeadlineRepository deadlines;
    private final VehicleAlertRepository alerts;
    private final DriverRepository drivers;
    private final Clock clock;

    public DriverAppService(DriverSpaceService space, VehicleService vehicles, FuelEntryRepository fuelEntries,
            InspectionRepository inspections, PhotoRepository photos, IncidentRepository incidents,
            DeadlineRepository deadlines, VehicleAlertRepository alerts, DriverRepository drivers, Clock clock) {
        this.space = space;
        this.vehicles = vehicles;
        this.fuelEntries = fuelEntries;
        this.inspections = inspections;
        this.photos = photos;
        this.incidents = incidents;
        this.deadlines = deadlines;
        this.alerts = alerts;
        this.drivers = drivers;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- fill-ups

    public FuelEntryView recordFuel(UUID driverId, UUID id, RecordFuel cmd) {
        Optional<FuelEntry> existing = fuelEntries.findById(id);
        if (existing.isPresent()) {
            return FuelEntryView.of(ownedBy(existing.get(), existing.get().getDriverId(), driverId), driverRef(driverId));
        }
        Vehicle vehicle = vehicles.load(space.currentVehicle(driverId));
        FuelEntry entry = fuelEntries.save(new FuelEntry(id, vehicle, driverId,
                new FuelEntry.Receipt(cmd.filledAt(), cmd.kind(), cmd.quantity(), cmd.amount(), cmd.mileage(),
                        cmd.station(), cmd.payment()),
                clock.instant()));
        vehicles.recordDriverReading(vehicle, cmd.mileage(), cmd.filledAt());
        return FuelEntryView.of(entry, driverRef(driverId));
    }

    @Transactional(readOnly = true)
    public List<FuelEntryView> fuelEntriesOfDriver(UUID driverId) {
        DriverRef driver = driverRef(driverId);
        return fuelEntries.findTop50ByDriverIdOrderByFilledAtDesc(driverId).stream()
                .map(e -> FuelEntryView.of(e, driver))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FuelEntryView> fuelEntriesOfVehicle(UUID vehicleId) {
        vehicles.load(vehicleId);
        List<FuelEntry> entries = fuelEntries.findTop50ByVehicleIdOrderByFilledAtDesc(vehicleId);
        Map<UUID, DriverRef> names = driverRefs(entries.stream().map(FuelEntry::getDriverId).toList());
        return entries.stream().map(e -> FuelEntryView.of(e, names.get(e.getDriverId()))).toList();
    }

    // ---------------------------------------------------------------- inspections

    public InspectionView recordInspection(UUID driverId, UUID id, RecordInspection cmd) {
        Optional<Inspection> existing = inspections.findById(id);
        if (existing.isPresent()) {
            return inspectionView(ownedBy(existing.get(), existing.get().getDriverId(), driverId), driverRef(driverId));
        }
        Vehicle vehicle = vehicles.load(space.currentVehicle(driverId));
        List<Inspection.Check> checks = cmd.checks().stream()
                .map(answer -> new Inspection.Check(answer.item(), answer.condition(), answer.comment()))
                .toList();
        Inspection inspection = inspections.save(new Inspection(id, vehicle.getId(), driverId, cmd.kind(),
                cmd.performedAt(), cmd.mileage(), cmd.fuelLevel(), cmd.remarks(), checks, clock.instant()));
        vehicles.recordDriverReading(vehicle, cmd.mileage(), cmd.performedAt());
        return inspectionView(inspection, driverRef(driverId));
    }

    @Transactional(readOnly = true)
    public List<InspectionView> inspectionsOfDriver(UUID driverId) {
        DriverRef driver = driverRef(driverId);
        return inspections.findTop20ByDriverIdOrderByPerformedAtDesc(driverId).stream()
                .map(i -> inspectionView(i, driver))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InspectionView> inspectionsOfVehicle(UUID vehicleId) {
        vehicles.load(vehicleId);
        List<Inspection> found = inspections.findTop20ByVehicleIdOrderByPerformedAtDesc(vehicleId);
        Map<UUID, DriverRef> names = driverRefs(found.stream().map(Inspection::getDriverId).toList());
        return found.stream().map(i -> inspectionView(i, names.get(i.getDriverId()))).toList();
    }

    // ---------------------------------------------------------------- photos

    /** Adds a photo to a claim or an inspection of the driver. */
    public PhotoView addPhoto(UUID driverId, Photo.Owner owner, UUID ownerId, UUID photoId, String contentType,
            byte[] content) {
        UUID vehicleId = switch (owner) {
            case INCIDENT -> incidents.findById(ownerId)
                    .filter(i -> i.getDriver() != null && i.getDriver().getId().equals(driverId))
                    .map(i -> i.getVehicle().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Incident", ownerId));
            case INSPECTION -> inspections.findById(ownerId)
                    .filter(i -> i.getDriverId().equals(driverId))
                    .map(Inspection::getVehicleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Inspection", ownerId));
        };
        Optional<Photo> existing = photos.findById(photoId);
        if (existing.isPresent()) {
            Photo photo = existing.get();
            if (photo.getOwnerType() != owner || !photo.getOwnerId().equals(ownerId)) {
                throw new BusinessRuleException("ID_ALREADY_USED", "This id is already used by another record");
            }
            return new PhotoView(photo.getId(), photo.getContentType(), photo.getSizeBytes(), photo.getCreatedAt());
        }
        if (photos.countByOwnerTypeAndOwnerId(owner, ownerId) >= Photo.MAX_PER_OWNER) {
            throw new BusinessRuleException("TOO_MANY_PHOTOS", "At most " + Photo.MAX_PER_OWNER + " photos per record");
        }
        Photo photo = photos.save(new Photo(photoId, owner, ownerId, vehicleId, contentType, content,
                clock.instant()));
        return new PhotoView(photo.getId(), photo.getContentType(), photo.getSizeBytes(), photo.getCreatedAt());
    }

    /** The photos of a claim, for a manager who can see the claim's vehicle. */
    @Transactional(readOnly = true)
    public List<PhotoView> incidentPhotos(UUID incidentId) {
        Incident incident = incidents.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));
        vehicles.load(incident.getVehicle().getId());
        return photos.summaries(Photo.Owner.INCIDENT, incidentId).stream().map(PhotoView::of).toList();
    }

    /**
     * The image itself. A driver only opens the photos of their own records; anyone else follows the usual rule of
     * the company of the vehicle.
     */
    @Transactional(readOnly = true)
    public Photo photo(UUID photoId, Optional<UUID> driverId) {
        Photo photo = photos.findById(photoId).orElseThrow(() -> new ResourceNotFoundException("Photo", photoId));
        if (driverId.isPresent()) {
            UUID owner = switch (photo.getOwnerType()) {
                case INCIDENT -> incidents.findById(photo.getOwnerId())
                        .map(Incident::getDriver).map(Driver::getId).orElse(null);
                case INSPECTION -> inspections.findById(photo.getOwnerId()).map(Inspection::getDriverId).orElse(null);
            };
            if (!driverId.get().equals(owner)) {
                throw new ResourceNotFoundException("Photo", photoId);
            }
        } else {
            vehicles.load(photo.getVehicleId());
        }
        return photo;
    }

    // ---------------------------------------------------------------- notifications

    /**
     * What the driver should know now: deadlines of their vehicle due within 30 days or overdue, alerts of the box of
     * their vehicle, and the progress of their claims. Most serious first.
     */
    @Transactional(readOnly = true)
    public List<DriverNotification> notifications(UUID driverId) {
        if (!drivers.existsById(driverId)) {
            throw new ResourceNotFoundException("Driver", driverId);
        }
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock.withZone(PARIS));
        Instant since = now.minus(NOTIFICATION_WINDOW);
        List<DriverNotification> result = new ArrayList<>();
        Optional<UUID> vehicleId = currentVehicleOf(driverId);
        vehicleId.ifPresent(id -> {
            for (Deadline deadline : deadlines.findByVehicleIdAndStatusOrderByDueDateAsc(id, Deadline.Status.OPEN)) {
                Urgency urgency = deadline.urgency(today, deadline.getVehicle().getMileage());
                if (urgency == Urgency.UPCOMING) {
                    continue;
                }
                Instant at = deadline.getDueDate() == null ? now : deadline.getDueDate().atStartOfDay(PARIS).toInstant();
                result.add(new DriverNotification("deadline:" + deadline.getId() + ":" + urgency, Kind.DEADLINE,
                        urgency == Urgency.OVERDUE ? Level.CRITICAL : Level.WARNING, deadline.getType().name(),
                        urgency.name(), deadline.getDueDate(), deadline.getDueMileage(), at));
            }
            for (VehicleAlert alert : alerts.findTop20ByVehicleIdAndRaisedAtAfterOrderByRaisedAtDesc(id, since)) {
                result.add(new DriverNotification("alert:" + alert.getId(), Kind.VEHICLE_ALERT, level(alert.getSeverity()),
                        alert.getType(), alert.getMessage(), null, null, alert.getRaisedAt()));
            }
        });
        for (Incident incident : incidents.findByDriverIdOrderByOccurredAtDesc(driverId)) {
            // The driver knows they declared it: what is news is what the fleet manager and the insurer did next.
            if (incident.getStatus() == Incident.Status.DECLARED || incident.getUpdatedAt().isBefore(since)) {
                continue;
            }
            result.add(new DriverNotification("incident:" + incident.getId() + ":" + incident.getStatus(),
                    Kind.INCIDENT, Level.INFO, incident.getStatus().name(), incident.getReference(), null, null,
                    incident.getUpdatedAt()));
        }
        result.sort(Comparator.comparing(DriverNotification::level).reversed()
                .thenComparing(DriverNotification::at, Comparator.reverseOrder()));
        return result;
    }

    private Optional<UUID> currentVehicleOf(UUID driverId) {
        try {
            return Optional.of(space.currentVehicle(driverId));
        } catch (BusinessRuleException noVehicle) {
            return Optional.empty();
        }
    }

    private static Level level(String severity) {
        return switch (severity) {
            case "CRITICAL" -> Level.CRITICAL;
            case "WARNING" -> Level.WARNING;
            default -> Level.INFO;
        };
    }

    // ---------------------------------------------------------------- helpers

    private InspectionView inspectionView(Inspection inspection, DriverRef driver) {
        List<PhotoView> pictures = photos.summaries(Photo.Owner.INSPECTION, inspection.getId()).stream()
                .map(PhotoView::of)
                .toList();
        return InspectionView.of(inspection, driver, pictures);
    }

    /** A record sent again by the app of its author is returned as it is; an id of someone else's record is refused. */
    private static <T> T ownedBy(T record, UUID author, UUID driverId) {
        if (!author.equals(driverId)) {
            throw new BusinessRuleException("ID_ALREADY_USED", "This id is already used by another record");
        }
        return record;
    }

    private DriverRef driverRef(UUID driverId) {
        return drivers.findById(driverId).map(DriverRef::of).orElse(null);
    }

    private Map<UUID, DriverRef> driverRefs(Collection<UUID> ids) {
        return drivers.findAllById(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(Driver::getId, DriverRef::of));
    }
}

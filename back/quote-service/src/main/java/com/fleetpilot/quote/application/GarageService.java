package com.fleetpilot.quote.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fleetpilot.common.error.ConflictException;
import com.fleetpilot.common.error.ResourceNotFoundException;
import com.fleetpilot.quote.application.command.QuoteCommands.Approval;
import com.fleetpilot.quote.application.command.QuoteCommands.SaveGarage;
import com.fleetpilot.quote.application.view.QuoteViews.GarageDetail;
import com.fleetpilot.quote.application.view.QuoteViews.GarageSummary;
import com.fleetpilot.quote.application.view.QuoteViews.NearbyGarage;
import com.fleetpilot.quote.domain.model.Garage;
import com.fleetpilot.quote.domain.repository.GarageRepository;
import com.fleetpilot.quote.domain.repository.GarageRepository.GarageDistance;
import com.fleetpilot.quote.domain.repository.QuoteSpecifications;
import com.fleetpilot.quote.domain.repository.QuoteSpecifications.GarageCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The garage network: workshops, their approval by the fleet manager and their track record. */
@Service
@Transactional
public class GarageService {

    private final GarageRepository garages;
    private final SavingsQuery savings;
    private final QuoteAccess access;
    private final Clock clock;

    public GarageService(GarageRepository garages, SavingsQuery savings, QuoteAccess access, Clock clock) {
        this.garages = garages;
        this.savings = savings;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<GarageSummary> search(GarageCriteria criteria, Pageable pageable) {
        return garages.findAll(QuoteSpecifications.garages(criteria), pageable).map(GarageSummary::of);
    }

    /** Approved garages within {@code radiusKm} of a point, nearest first: where a driver can take the vehicle. */
    @Transactional(readOnly = true)
    public List<NearbyGarage> nearby(double latitude, double longitude, double radiusKm, int limit) {
        List<GarageDistance> found = garages.approvedNear(latitude, longitude, radiusKm, limit);
        Map<UUID, Garage> byId = garages.findAllById(found.stream().map(GarageDistance::getId).toList()).stream()
                .collect(Collectors.toMap(Garage::getId, Function.identity()));
        return found.stream().map(d -> NearbyGarage.of(byId.get(d.getId()), d.getDistanceKm())).toList();
    }

    /** A garage sees its own file only; the statistics are computed on the quotes the caller may see. */
    @Transactional(readOnly = true)
    public GarageDetail get(UUID id) {
        access.restrictedGarageId().filter(own -> !own.equals(id)).ifPresent(own -> {
            throw new ResourceNotFoundException("Garage", id);
        });
        Garage garage = load(id);
        return GarageDetail.of(garage, savings.garageStats(id, access.restrictedCompanyId().orElse(null)));
    }

    public GarageDetail create(SaveGarage cmd) {
        garages.findBySiret(cmd.siret()).ifPresent(existing -> {
            throw new ConflictException("GARAGE_ALREADY_EXISTS", "A garage with SIRET " + cmd.siret() + " already exists");
        });
        String code = cmd.postalCode().substring(0, 2) + "-" + cmd.siret().substring(9).toUpperCase(Locale.ROOT);
        Garage garage = garages.save(new Garage(UUID.randomUUID(), code, profile(cmd)));
        return GarageDetail.of(garage, savings.garageStats(garage.getId(), null));
    }

    public GarageDetail update(UUID id, SaveGarage cmd) {
        Garage garage = load(id);
        garages.findBySiret(cmd.siret()).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new ConflictException("GARAGE_ALREADY_EXISTS", "A garage with SIRET " + cmd.siret() + " already exists");
        });
        garage.apply(profile(cmd));
        return GarageDetail.of(garage, savings.garageStats(id, null));
    }

    /** Grants or withdraws the approval, and records the quality rating of the fleet manager. */
    public GarageDetail approve(UUID id, Approval cmd) {
        Garage garage = load(id);
        garage.setApproved(cmd.approved(), LocalDate.now(clock));
        garage.rate(cmd.rating());
        return GarageDetail.of(garage, savings.garageStats(id, null));
    }

    private Garage load(UUID id) {
        return garages.findById(id).orElseThrow(() -> new ResourceNotFoundException("Garage", id));
    }

    private static Garage.Profile profile(SaveGarage cmd) {
        return new Garage.Profile(cmd.name(), cmd.network(), cmd.brand(), cmd.siret(), cmd.address(), cmd.postalCode(),
                cmd.city(), cmd.latitude(), cmd.longitude(), cmd.email(), cmd.phone(), cmd.hourlyRate());
    }
}

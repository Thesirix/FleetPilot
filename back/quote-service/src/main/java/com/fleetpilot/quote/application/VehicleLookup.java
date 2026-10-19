package com.fleetpilot.quote.application;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import com.fleetpilot.common.util.RegistrationNumbers;
import com.fleetpilot.quote.application.VehicleDirectory.RemoteVehicle;
import com.fleetpilot.quote.domain.model.VehicleRef;
import com.fleetpilot.quote.domain.repository.VehicleRefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

/**
 * Finds the vehicle a garage writes about. The local copy (fed by the fleet events) answers almost always; a vehicle
 * created a second ago may not have arrived yet, so the fleet module is asked directly before giving up.
 */
@Service
@Transactional
public class VehicleLookup {

    private static final Logger LOG = LoggerFactory.getLogger(VehicleLookup.class);

    private final VehicleRefRepository vehicles;
    private final VehicleDirectory directory;
    private final Clock clock;

    public VehicleLookup(VehicleRefRepository vehicles, VehicleDirectory directory, Clock clock) {
        this.vehicles = vehicles;
        this.directory = directory;
        this.clock = clock;
    }

    /** The vehicle with this registration (any spelling), or empty when the fleet module does not know it. */
    public Optional<VehicleRef> resolve(String registration) {
        Optional<String> normalized = RegistrationNumbers.normalize(registration);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        Optional<VehicleRef> local = vehicles.findByRegistration(normalized.get());
        if (local.isPresent()) {
            return local;
        }
        try {
            return directory.findByRegistration(normalized.get()).map(this::save);
        } catch (RestClientException e) {
            LOG.warn("Fleet module unreachable, vehicle {} left unidentified: {}", normalized.get(), e.getMessage());
            return Optional.empty();
        }
    }

    /** Copies every vehicle of the fleet module (first start, or after a restore). Returns the number copied. */
    public int synchronizeAll() {
        List<RemoteVehicle> all = directory.findAll();
        all.forEach(this::save);
        LOG.info("Vehicle reference synchronized: {} vehicles", all.size());
        return all.size();
    }

    public VehicleRef save(RemoteVehicle v) {
        VehicleRef ref = new VehicleRef(v.id(), v.companyId(), v.registration(), v.make(), v.model(), v.category(),
                v.energy(), v.city(), clock.instant());
        return vehicles.save(ref);
    }
}

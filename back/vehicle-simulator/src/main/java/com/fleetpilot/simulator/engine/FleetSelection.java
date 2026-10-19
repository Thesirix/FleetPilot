package com.fleetpilot.simulator.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fleetpilot.common.telemetry.ConnectedFleet;
import com.fleetpilot.simulator.engine.VehicleProfile.Behaviour;

/**
 * Chooses the connected vehicles among the fleet: vehicles in service, fitted with a box, based at a site of the road
 * network, spread evenly over the fleet (always the same ones for the same fleet). A few drivers get a behaviour that
 * the alert rules of the telemetry service must catch.
 */
public final class FleetSelection {

    /** A vehicle of the fleet module, with the fields the selection needs. */
    public record FleetVehicle(UUID id, UUID companyId, String registration, String category, String energy,
            String status, String site, int mileage) {
    }

    private FleetSelection() {
    }

    public static List<VehicleProfile> select(List<FleetVehicle> fleet, Set<String> sites, int count) {
        List<FleetVehicle> eligible = fleet.stream()
                .filter(v -> "ACTIVE".equals(v.status()) && v.site() != null && sites.contains(v.site()))
                // only the vehicles fitted with a box send telemetry
                .filter(v -> ConnectedFleet.isEquipped(v.id()))
                .sorted(Comparator.comparing(FleetVehicle::id))
                .toList();
        if (eligible.isEmpty() || count <= 0) {
            return List.of();
        }
        double step = Math.max(1.0, (double) eligible.size() / count);
        List<FleetVehicle> chosen = new ArrayList<>();
        for (double i = 0; i < eligible.size() && chosen.size() < count; i += step) {
            chosen.add(eligible.get((int) i));
        }
        List<VehicleProfile> profiles = new ArrayList<>();
        for (int i = 0; i < chosen.size(); i++) {
            FleetVehicle v = chosen.get(i);
            profiles.add(new VehicleProfile(v.id(), v.companyId(), v.registration(), v.category(), v.energy(), v.site(),
                    v.mileage(), behaviour(i)));
        }
        return profiles;
    }

    /** About 4 % of speeders, 4 % of idlers and 2.5 % of vehicles with a recurring engine fault. */
    static Behaviour behaviour(int index) {
        if (index % 25 == 3) {
            return Behaviour.SPEEDER;
        }
        if (index % 25 == 11) {
            return Behaviour.IDLER;
        }
        if (index % 40 == 19) {
            return Behaviour.FAULTY;
        }
        return Behaviour.CALM;
    }
}

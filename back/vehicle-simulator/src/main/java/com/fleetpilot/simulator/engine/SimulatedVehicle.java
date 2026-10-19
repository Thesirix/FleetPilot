package com.fleetpilot.simulator.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import com.fleetpilot.common.telemetry.TelemetryMessages.FaultReport;
import com.fleetpilot.common.telemetry.TelemetryMessages.MileageReport;
import com.fleetpilot.common.telemetry.TelemetryMessages.PositionReport;
import com.fleetpilot.common.telemetry.TelemetryMessages.Severity;
import com.fleetpilot.simulator.road.RoadNetwork;
import com.fleetpilot.simulator.road.Route;
import com.fleetpilot.simulator.road.Route.Point;

/**
 * One connected vehicle: it leaves its site for a business place on a real road, stops there, comes back, and so on.
 *
 * <ul>
 *   <li>PARKED: ignition off, a heartbeat position every minute; an electric vehicle charges at its site;</li>
 *   <li>DRIVING: a position at every tick, the odometer every minute; the speed follows the estimate of the routing
 *       engine with the variations of the traffic;</li>
 *   <li>IDLING: stopped with the engine running (some drivers wait like this at the customers).</li>
 * </ul>
 */
public final class SimulatedVehicle {

    static final Duration HEARTBEAT = Duration.ofMinutes(1);
    static final Duration MILEAGE_EVERY = Duration.ofMinutes(1);
    /** Speed limit of a fast road by category, used by the speeding drivers. */
    private static final int MOTORWAY_KMH = 130;

    enum State {
        PARKED,
        DRIVING,
        IDLING
    }

    /** Trouble codes a box may report, with their meaning and severity. */
    record Trouble(String code, String description, Severity severity) {
    }

    /** Codes of a combustion engine (petrol, diesel, hybrid); the first one is the serious fault of the demos. */
    static final List<Trouble> COMBUSTION_TROUBLES = List.of(
            new Trouble("P0300", "Ratés d'allumage détectés sur plusieurs cylindres", Severity.HIGH),
            new Trouble("P0217", "Surchauffe du moteur", Severity.HIGH),
            new Trouble("P0420", "Efficacité du catalyseur insuffisante", Severity.MEDIUM),
            new Trouble("P0171", "Mélange trop pauvre", Severity.MEDIUM),
            new Trouble("P0562", "Tension du système électrique trop faible", Severity.MEDIUM),
            new Trouble("P0128", "Température du liquide de refroidissement trop basse", Severity.LOW),
            new Trouble("C0035", "Capteur de vitesse de roue avant gauche", Severity.LOW));

    /** Codes of an electric drive train: no ignition, no exhaust, but a high voltage battery. */
    static final List<Trouble> ELECTRIC_TROUBLES = List.of(
            new Trouble("P0AA6", "Défaut d'isolement du circuit haute tension", Severity.HIGH),
            new Trouble("P0A7F", "Dégradation de la batterie de traction", Severity.MEDIUM),
            new Trouble("P0C73", "Pompe de refroidissement de l'électronique de puissance", Severity.MEDIUM),
            new Trouble("P0562", "Tension de la batterie 12 V trop faible", Severity.MEDIUM),
            new Trouble("C0035", "Capteur de vitesse de roue avant gauche", Severity.LOW));

    /** Every code known by the simulator, for the faults injected by hand. */
    static final List<Trouble> TROUBLES = Stream.concat(COMBUSTION_TROUBLES.stream(),
            ELECTRIC_TROUBLES.stream()).distinct().toList();

    private final VehicleProfile profile;
    private final RoadNetwork roads;
    private final Random random;

    private State state = State.PARKED;
    private Route route;
    private double progress;
    private double odometerKm;
    private Double battery;
    private double latitude;
    private double longitude;
    private int heading;
    private int speed;
    private boolean atSite = true;
    private Instant until;
    private Instant lastHeartbeat = Instant.EPOCH;
    private Instant lastMileage = Instant.EPOCH;
    private double cruise;
    private Trouble pendingFault;

    public SimulatedVehicle(VehicleProfile profile, RoadNetwork roads, long seed) {
        this.profile = profile;
        this.roads = roads;
        this.random = new Random(seed);
        this.odometerKm = profile.mileage();
        this.battery = profile.electric() ? 55.0 + random.nextInt(45) : null;
        double[] site = roads.site(profile.site());
        this.latitude = site[0];
        this.longitude = site[1];
    }

    /**
     * Places the vehicle somewhere in its day at the start of the simulation, so that the map is alive at once: half
     * of the vehicles are already on the road.
     */
    public void start(Instant now) {
        if (random.nextBoolean()) {
            startTrip(now);
            progress = route.length() * random.nextDouble() * 0.8;
        } else {
            park(now, Duration.ofSeconds(random.nextInt(15 * 60)));
        }
    }

    /** Advances the vehicle by {@code seconds} and sends what its box would send. */
    public void tick(Instant now, double seconds, TelemetrySink sink) {
        switch (state) {
            case PARKED -> parked(now, seconds, sink);
            case DRIVING -> drive(now, seconds, sink);
            default -> idle(now, sink);
        }
    }

    private void parked(Instant now, double seconds, TelemetrySink sink) {
        if (battery != null && atSite && battery < 95) {
            // 7 kW wallbox: about 1 % of a 60 kWh battery every 5 minutes
            battery = Math.min(100, battery + seconds / 300);
        }
        if (!now.isBefore(until)) {
            startTrip(now);
            drive(now, 0, sink);
            return;
        }
        if (Duration.between(lastHeartbeat, now).compareTo(HEARTBEAT) >= 0) {
            sink.position(position(now, false));
            lastHeartbeat = now;
        }
    }

    private void drive(Instant now, double seconds, TelemetrySink sink) {
        speed = targetSpeed();
        double metres = speed / 3.6 * seconds;
        progress += metres;
        odometerKm += metres / 1000;
        if (battery != null) {
            battery = Math.max(3, battery - metres / 1000 / profile.rangeKm() * 100);
        }
        Point point = route.at(progress);
        latitude = point.latitude();
        longitude = point.longitude();
        heading = point.heading();
        if (progress >= route.length()) {
            arrive(now, sink);
            return;
        }
        sink.position(position(now, true));
        reportPendingFault(now, sink);
        if (Duration.between(lastMileage, now).compareTo(MILEAGE_EVERY) >= 0) {
            sink.mileage(mileage(now));
            lastMileage = now;
        }
    }

    private void idle(Instant now, TelemetrySink sink) {
        speed = 0;
        if (!now.isBefore(until)) {
            park(now, Duration.ofMinutes(3 + random.nextInt(10)));
            sink.position(position(now, false));
            return;
        }
        sink.position(position(now, true));
    }

    private void arrive(Instant now, TelemetrySink sink) {
        speed = 0;
        atSite = route.to().equals(profile.site());
        sink.position(position(now, true));
        sink.mileage(mileage(now));
        lastMileage = now;
        boolean idles = !atSite && (profile.behaviour() == VehicleProfile.Behaviour.IDLER ? random.nextDouble() < 0.7
                : random.nextDouble() < 0.04);
        if (idles) {
            state = State.IDLING;
            until = now.plus(Duration.ofMinutes(12 + random.nextInt(10)));
        } else {
            park(now, atSite ? Duration.ofMinutes(15 + random.nextInt(60)) : Duration.ofMinutes(8 + random.nextInt(35)));
            sink.position(position(now, false));
        }
    }

    private void startTrip(Instant now) {
        if (atSite) {
            List<Route> routes = roads.routesFrom(profile.site());
            route = routes.get(random.nextInt(routes.size()));
        } else {
            route = route.reversed();
        }
        state = State.DRIVING;
        progress = 0;
        cruise = 0.88 + random.nextDouble() * 0.2;
        lastMileage = now;
        double faultChance = profile.behaviour() == VehicleProfile.Behaviour.FAULTY ? 0.35 : 0.01;
        if (random.nextDouble() < faultChance) {
            List<Trouble> troubles = profile.electric() ? ELECTRIC_TROUBLES : COMBUSTION_TROUBLES;
            pendingFault = profile.behaviour() == VehicleProfile.Behaviour.FAULTY ? troubles.getFirst()
                    : troubles.get(random.nextInt(troubles.size()));
        }
    }

    /** A trouble code drawn at the start of a trip is reported once the vehicle has driven a little. */
    private void reportPendingFault(Instant now, TelemetrySink sink) {
        if (pendingFault != null && progress > 1_500) {
            sink.fault(new FaultReport(profile.id(), profile.companyId(), profile.registration(), now, pendingFault.code(),
                    pendingFault.description(), pendingFault.severity()));
            pendingFault = null;
        }
    }

    private void park(Instant now, Duration duration) {
        state = State.PARKED;
        speed = 0;
        until = now.plus(duration);
        lastHeartbeat = now;
    }

    /** Speed of the traffic on this road, with its variations; a speeding driver goes well above it on fast roads. */
    private int targetSpeed() {
        double base = route.averageSpeedKmh() * cruise;
        double traffic = 0.75 + random.nextDouble() * 0.5;
        double kmh = base * traffic;
        if (profile.behaviour() == VehicleProfile.Behaviour.SPEEDER && route.averageSpeedKmh() > 65) {
            kmh = Math.max(kmh, limit() + 18 + random.nextInt(22));
        }
        if (progress < 120 || route.length() - progress < 120) {
            kmh = Math.min(kmh, 25);
        }
        return (int) Math.round(Math.clamp(kmh, 5, 175));
    }

    private int limit() {
        return switch (profile.category()) {
            case "TRUCK", "MACHINE" -> 90;
            case "VAN" -> 110;
            default -> MOTORWAY_KMH;
        };
    }

    private PositionReport position(Instant now, boolean ignition) {
        return new PositionReport(profile.id(), profile.companyId(), profile.registration(), now,
                Math.round(latitude * 1e6) / 1e6, Math.round(longitude * 1e6) / 1e6, ignition ? speed : 0, heading,
                ignition, battery == null ? null : (int) Math.round(battery));
    }

    private MileageReport mileage(Instant now) {
        return new MileageReport(profile.id(), profile.companyId(), profile.registration(), now, (int) odometerKm);
    }

    public VehicleProfile profile() {
        return profile;
    }

    public boolean driving() {
        return state == State.DRIVING;
    }

    State state() {
        return state;
    }

    double odometerKm() {
        return odometerKm;
    }

    Double battery() {
        return battery;
    }
}

package com.fleetpilot.simulator.engine;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import com.fleetpilot.common.telemetry.TelemetryMessages.FaultReport;
import com.fleetpilot.common.telemetry.TelemetryMessages.MileageReport;
import com.fleetpilot.common.telemetry.TelemetryMessages.PositionReport;
import com.fleetpilot.common.telemetry.TelemetryMessages.Severity;
import com.fleetpilot.simulator.road.RoadNetwork;

/** All the simulated vehicles, advanced together at every tick. */
public class Simulation {

    /** What the simulation is doing, for the status endpoint. */
    public record Status(boolean running, int vehicles, long driving, long messages, Instant startedAt) {
    }

    private final RoadNetwork roads;
    private final Clock clock;
    private final List<SimulatedVehicle> vehicles = new CopyOnWriteArrayList<>();
    private final AtomicLong messages = new AtomicLong();
    private final TelemetrySink counting;
    private Instant lastTick;
    private Instant startedAt;

    public Simulation(RoadNetwork roads, TelemetrySink sink, Clock clock) {
        this.roads = roads;
        this.clock = clock;
        this.counting = new TelemetrySink() {
            @Override
            public void position(PositionReport report) {
                messages.incrementAndGet();
                sink.position(report);
            }

            @Override
            public void mileage(MileageReport report) {
                messages.incrementAndGet();
                sink.mileage(report);
            }

            @Override
            public void fault(FaultReport report) {
                messages.incrementAndGet();
                sink.fault(report);
            }
        };
    }

    public synchronized void start(List<VehicleProfile> profiles) {
        Instant now = clock.instant();
        vehicles.clear();
        for (VehicleProfile profile : profiles) {
            SimulatedVehicle vehicle = new SimulatedVehicle(profile, roads, profile.id().getMostSignificantBits());
            vehicle.start(now);
            vehicles.add(vehicle);
        }
        lastTick = now;
        startedAt = now;
    }

    /** Advances every vehicle by the time elapsed since the previous tick. */
    public synchronized void tick() {
        if (lastTick == null) {
            return;
        }
        Instant now = clock.instant();
        double seconds = Math.min(30, Math.max(0, (now.toEpochMilli() - lastTick.toEpochMilli()) / 1000.0));
        lastTick = now;
        for (SimulatedVehicle vehicle : vehicles) {
            vehicle.tick(now, seconds, counting);
        }
    }

    /** For demonstrations: a vehicle reports an engine fault now (null code: the serious fault of its drive train). */
    public Optional<FaultReport> injectFault(String registration, String code) {
        return vehicles.stream().filter(v -> v.profile().registration().equals(registration)).findFirst().map(v -> {
            SimulatedVehicle.Trouble trouble = code == null
                    ? (v.profile().electric() ? SimulatedVehicle.ELECTRIC_TROUBLES : SimulatedVehicle.COMBUSTION_TROUBLES)
                            .getFirst()
                    : SimulatedVehicle.TROUBLES.stream().filter(t -> t.code().equals(code)).findFirst()
                            .orElse(new SimulatedVehicle.Trouble(code, "Défaut signalé par le boîtier", Severity.HIGH));
            FaultReport report = new FaultReport(v.profile().id(), v.profile().companyId(), registration, clock.instant(),
                    trouble.code(), trouble.description(), trouble.severity());
            counting.fault(report);
            return report;
        });
    }

    public Status status() {
        return new Status(lastTick != null, vehicles.size(), vehicles.stream().filter(SimulatedVehicle::driving).count(),
                messages.get(), startedAt);
    }

    List<SimulatedVehicle> vehicles() {
        return vehicles;
    }
}

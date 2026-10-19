package com.fleetpilot.simulator.api;

import com.fleetpilot.common.error.ResourceNotFoundException;
import com.fleetpilot.common.security.Roles;
import com.fleetpilot.common.telemetry.TelemetryMessages.FaultReport;
import com.fleetpilot.simulator.engine.Simulation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Control of the simulator, for demonstrations: what it is doing, and a fault on demand. */
@RestController
@RequestMapping("/api/v1/simulator")
@PreAuthorize(Roles.ADMIN_ONLY)
public class SimulatorController {

    private final Simulation simulation;

    public SimulatorController(Simulation simulation) {
        this.simulation = simulation;
    }

    public record InjectFault(String code) {
    }

    @GetMapping
    public Simulation.Status status() {
        return simulation.status();
    }

    /**
     * The box of a vehicle reports a trouble code now. Without a code, the serious fault of its drive train: misfire
     * for a combustion engine, high voltage insulation fault for an electric one.
     */
    @PostMapping("/vehicles/{registration}/fault")
    public FaultReport fault(@PathVariable String registration, @RequestBody InjectFault command) {
        String code = command.code() == null || command.code().isBlank() ? null : command.code();
        return simulation.injectFault(registration, code)
                .orElseThrow(() -> new ResourceNotFoundException("Simulated vehicle", registration));
    }
}

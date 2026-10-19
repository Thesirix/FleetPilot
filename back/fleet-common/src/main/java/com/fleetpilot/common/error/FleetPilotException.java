package com.fleetpilot.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base class of every business exception raised by FleetPilot services.
 *
 * <p>Each exception carries a stable, machine-readable {@code code} (for example {@code VEHICLE_NOT_FOUND})
 * that clients can rely on, and an HTTP status used by {@link ApiExceptionHandler} to build the response.
 */
public abstract class FleetPilotException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    protected FleetPilotException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public abstract HttpStatus getStatus();
}

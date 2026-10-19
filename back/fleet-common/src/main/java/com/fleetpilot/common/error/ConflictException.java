package com.fleetpilot.common.error;

import org.springframework.http.HttpStatus;

/** The request conflicts with the current state (duplicate registration number, concurrent update...). */
public class ConflictException extends FleetPilotException {

    private static final long serialVersionUID = 1L;

    public ConflictException(String code, String message) {
        super(code, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}

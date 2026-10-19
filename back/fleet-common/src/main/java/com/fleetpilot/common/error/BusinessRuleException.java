package com.fleetpilot.common.error;

import org.springframework.http.HttpStatus;

/** The request is well formed but breaks a business rule (for example assigning a vehicle that is in repair). */
public class BusinessRuleException extends FleetPilotException {

    private static final long serialVersionUID = 1L;

    public BusinessRuleException(String code, String message) {
        super(code, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.UNPROCESSABLE_CONTENT;
    }
}

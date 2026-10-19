package com.fleetpilot.common.error;

import java.util.Locale;

import org.springframework.http.HttpStatus;

/** A requested resource does not exist, or is not visible to the caller's client company. */
public class ResourceNotFoundException extends FleetPilotException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String resource, Object id) {
        super(resource.toUpperCase(Locale.ROOT).replace(' ', '_') + "_NOT_FOUND", resource + " " + id + " not found");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }
}

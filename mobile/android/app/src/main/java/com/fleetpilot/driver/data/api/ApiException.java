package com.fleetpilot.driver.data.api;

import androidx.annotation.Nullable;

/**
 * A failed call. Transient when the network, the token or the server is unavailable (the outbox tries again later);
 * otherwise the server refused the request for a business reason, given by its stable code (IMPLAUSIBLE_PRICE...).
 */
public final class ApiException extends Exception {

    private final int status;
    private final String code;
    private final boolean transientFailure;

    private ApiException(String message, int status, String code, boolean transientFailure, @Nullable Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.transientFailure = transientFailure;
    }

    public static ApiException network(Throwable cause) {
        return new ApiException("Network unavailable", 0, "NETWORK_ERROR", true, cause);
    }

    public static ApiException http(int status, @Nullable ApiModels.Problem problem) {
        String code = problem != null && problem.code != null ? problem.code : "HTTP_" + status;
        String message = problem != null && problem.detail != null ? problem.detail : "HTTP " + status;
        boolean retry = status >= 500 || status == 401 || status == 408 || status == 429;
        return new ApiException(message, status, code, retry, null);
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public boolean isTransient() {
        return transientFailure;
    }
}

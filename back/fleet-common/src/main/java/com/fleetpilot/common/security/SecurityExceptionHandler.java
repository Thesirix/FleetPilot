package com.fleetpilot.common.security;

import com.fleetpilot.common.error.ApiExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Method security ({@code @PreAuthorize}) refuses access from inside the controller; without this handler the
 * catch-all of {@link ApiExceptionHandler} would turn the refusal into a 500.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return ApiExceptionHandler.problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Your role does not allow this operation.");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        return ApiExceptionHandler.problem(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "A valid access token is required.");
    }
}

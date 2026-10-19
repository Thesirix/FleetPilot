package com.fleetpilot.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Database-related errors, in services that use one. Ordered before {@link ApiExceptionHandler}, whose catch-all
 * handler would otherwise turn them into 500 errors.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PersistenceExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(PersistenceExceptionHandler.class);

    /** Two users saved the same record at the same time: the second one must reload. */
    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ProblemDetail handleConcurrentUpdate(RuntimeException ex) {
        LOG.info("Concurrent update rejected: {}", ex.getMessage());
        return ApiExceptionHandler.problem(HttpStatus.CONFLICT, "STALE_VERSION",
                "The record was modified by someone else in the meantime. Reload it and try again.");
    }

    /** A database constraint caught what the business checks did not (usually a race on a unique value). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleIntegrityViolation(DataIntegrityViolationException ex) {
        LOG.warn("Integrity constraint violated: {}", ex.getMostSpecificCause().getMessage());
        return ApiExceptionHandler.problem(HttpStatus.CONFLICT, "DATA_CONFLICT",
                "The change conflicts with existing data (duplicate value or record in use).");
    }

    /** Unknown property in a {@code sort} parameter. */
    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail handleUnknownSortProperty(PropertyReferenceException ex) {
        return ApiExceptionHandler.problem(HttpStatus.BAD_REQUEST, "INVALID_SORT",
                "Cannot sort by '" + ex.getPropertyName() + "'.");
    }
}

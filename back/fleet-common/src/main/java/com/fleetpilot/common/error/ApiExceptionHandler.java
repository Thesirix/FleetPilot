package com.fleetpilot.common.error;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.fleetpilot.common.web.CorrelationIdFilter;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;

/**
 * Turns every error into an RFC 9457 "problem detail" with the same shape across all FleetPilot services.
 *
 * <pre>{@code
 * {
 *   "type": "https://fleetpilot.dev/problems/vehicle-not-found",
 *   "title": "Not Found",
 *   "status": 404,
 *   "detail": "Vehicle 42 not found",
 *   "code": "VEHICLE_NOT_FOUND",
 *   "correlationId": "4f1c...",
 *   "timestamp": "2026-10-14T09:12:00Z",
 *   "errors": [ { "field": "vin", "message": "must be a valid VIN" } ]
 * }
 * }</pre>
 *
 * Unexpected exceptions are logged with their stack trace but never leak internal details to the client.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    static final String PROBLEM_BASE_URI = "https://fleetpilot.dev/problems/";

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * One invalid field of a request body or parameter. {@code constraint} names the rule (NotBlank, Size, Pattern,
     * Email, DecimalMin...) and {@code params} its limits, so that a client can write the message in its own language;
     * {@code message} stays the English text for API users.
     */
    public record FieldViolation(String field, String message, String constraint, Map<String, Object> params) {

        public FieldViolation(String field, String message) {
            this(field, message, null, Map.of());
        }

        static FieldViolation of(String field, String message, ConstraintViolation<?> violation) {
            if (violation == null) {
                return new FieldViolation(field, message);
            }
            var descriptor = violation.getConstraintDescriptor();
            Map<String, Object> params = new TreeMap<>();
            descriptor.getAttributes().forEach((name, value) -> {
                if (PARAMS.contains(name) && (value instanceof Number || value instanceof String || value instanceof Boolean)) {
                    params.put(name, value);
                }
            });
            return new FieldViolation(field, message, descriptor.getAnnotation().annotationType().getSimpleName(), params);
        }
    }

    private static final Set<String> PARAMS = Set.of("min", "max", "value", "integer", "fraction", "inclusive");

    @ExceptionHandler(FleetPilotException.class)
    public ProblemDetail handleFleetPilotException(FleetPilotException ex) {
        if (ex.getStatus().is5xxServerError()) {
            LOG.error("Business error {}", ex.getCode(), ex);
        } else {
            LOG.info("Request rejected: {} ({})", ex.getCode(), ex.getMessage());
        }
        return problem(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "The request contains invalid values.");
        List<FieldViolation> errors = ex.getConstraintViolations().stream()
                .map(v -> FieldViolation.of(lastNode(v.getPropertyPath().toString()), v.getMessage(), v))
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        LOG.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Quote the correlation id when contacting support.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "The request contains invalid values.");
        List<FieldViolation> errors = ex.getBindingResult().getAllErrors().stream()
                .map(error -> FieldViolation.of(
                        error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName(),
                        error.getDefaultMessage(), violation(error)))
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().headers(headers).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "The request contains invalid values.");
        List<FieldViolation> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> FieldViolation.of(fieldName(error, result.getMethodParameter().getParameterName()),
                                error.getDefaultMessage(), violation(error))))
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().headers(headers).body(problem);
    }

    /** Unreadable body: says which field has a wrong type, when Jackson can tell. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "The request body is not valid JSON or contains a value of the wrong type.");
        if (ex.getMostSpecificCause() instanceof JacksonException jackson && !jackson.getPath().isEmpty()) {
            String field = jackson.getPath().stream()
                    .map(reference -> reference.getPropertyName() != null ? reference.getPropertyName()
                            : "[" + reference.getIndex() + "]")
                    .reduce((a, b) -> b.startsWith("[") ? a + b : a + "." + b)
                    .orElse("body");
            problem.setProperty("errors", List.of(new FieldViolation(field, "has an invalid value")));
        }
        return ResponseEntity.badRequest().headers(headers).body(problem);
    }

    /** Framework errors (bad JSON, unsupported media type, missing parameter...) keep Spring's status and text. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers, HttpStatusCode statusCode,
            WebRequest request) {
        if (body instanceof ProblemDetail problem && problem.getProperties() == null) {
            enrich(problem, codeFor(statusCode));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    public static ProblemDetail problem(HttpStatusCode status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        enrich(problem, code);
        return problem;
    }

    private static void enrich(ProblemDetail problem, String code) {
        problem.setType(URI.create(PROBLEM_BASE_URI + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setProperty("code", code);
        problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        problem.setProperty("timestamp", Instant.now().toString());
    }

    private static String codeFor(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return known == null ? "HTTP_" + status.value() : known.name();
    }

    private static String fieldName(MessageSourceResolvable error, String parameterName) {
        return error instanceof FieldError fieldError ? fieldError.getField() : parameterName;
    }

    /** The Bean Validation violation behind a Spring error, when there is one. */
    private static ConstraintViolation<?> violation(Object error) {
        if (error instanceof ObjectError objectError) {
            try {
                return objectError.unwrap(ConstraintViolation.class);
            } catch (IllegalArgumentException notAViolation) {
                return null;
            }
        }
        return null;
    }

    private static String lastNode(String propertyPath) {
        int dot = propertyPath.lastIndexOf('.');
        return dot < 0 ? propertyPath : propertyPath.substring(dot + 1);
    }
}

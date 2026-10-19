package com.fleetpilot.common.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;

import com.fleetpilot.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Answers 401 and 403 raised by the security filters with the same problem-detail format as every other error,
 * instead of an empty body.
 */
public class ProblemSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        write(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "A valid access token is required.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        write(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Your role does not allow this operation.");
    }

    /** The values written are constants or a validated correlation id: no escaping is needed. */
    static void write(HttpServletResponse response, HttpStatus status, String code, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        response.getWriter().write("""
                {"type":"https://fleetpilot.dev/problems/%s","title":"%s","status":%d,"detail":"%s","code":"%s",\
                "correlationId":%s,"timestamp":"%s"}"""
                .formatted(code.toLowerCase(Locale.ROOT).replace('_', '-'), status.getReasonPhrase(),
                        status.value(), detail, code, correlationId == null ? "null" : "\"" + correlationId + "\"",
                        Instant.now()));
    }
}

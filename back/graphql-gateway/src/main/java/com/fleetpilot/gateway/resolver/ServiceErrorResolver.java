package com.fleetpilot.gateway.resolver;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns the problem details returned by a service (RFC 9457) into GraphQL errors, keeping the stable {@code code},
 * the field errors and the correlation id, so the portal handles errors the same way whatever the service.
 */
@Component
public class ServiceErrorResolver extends DataFetcherExceptionResolverAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceErrorResolver.class);

    private final JsonMapper json;

    public ServiceErrorResolver(JsonMapper json) {
        this.json = json;
    }

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        if (ex instanceof RestClientResponseException response) {
            return fromProblem(response, env);
        }
        if (ex instanceof ResourceAccessException) {
            LOG.error("Service unreachable while resolving {}", env.getField().getName(), ex);
            return GraphqlErrorBuilder.newError(env).errorType(ErrorType.INTERNAL_ERROR)
                    .message("A FleetPilot service is temporarily unavailable. Please try again in a moment.")
                    .extensions(Map.of("code", "SERVICE_UNAVAILABLE", "status", 503))
                    .build();
        }
        return null;
    }

    private GraphQLError fromProblem(RestClientResponseException response, DataFetchingEnvironment env) {
        Map<String, Object> problem = parse(response.getResponseBodyAsString());
        int status = response.getStatusCode().value();
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("code", problem.getOrDefault("code", "HTTP_" + status));
        extensions.put("status", status);
        if (problem.get("errors") instanceof List<?> errors) {
            extensions.put("fieldErrors", errors);
        }
        if (problem.get("correlationId") != null) {
            extensions.put("correlationId", problem.get("correlationId"));
        }
        String message = (String) problem.getOrDefault("detail", HttpStatus.valueOf(status).getReasonPhrase());
        return GraphqlErrorBuilder.newError(env).errorType(errorType(status)).message(message).extensions(extensions)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String body) {
        try {
            return body == null || body.isBlank() ? Map.of() : json.readValue(body, Map.class);
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    static ErrorType errorType(int status) {
        return switch (status) {
            case 400, 409, 422 -> ErrorType.BAD_REQUEST;
            case 401 -> ErrorType.UNAUTHORIZED;
            case 403 -> ErrorType.FORBIDDEN;
            case 404 -> ErrorType.NOT_FOUND;
            default -> ErrorType.INTERNAL_ERROR;
        };
    }
}

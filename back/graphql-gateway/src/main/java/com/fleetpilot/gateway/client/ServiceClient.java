package com.fleetpilot.gateway.client;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * Calls one REST service on behalf of the user. JSON answers are returned as plain maps and lists: the GraphQL schema
 * is the contract, so the gateway does not duplicate the DTOs of every service.
 */
public class ServiceClient {

    private final RestClient rest;

    public ServiceClient(RestClient rest) {
        this.rest = rest;
    }

    public Object get(String path, Map<String, ?> query, Object... pathVariables) {
        return rest.get()
                .uri(builder -> build(builder, path, query, pathVariables))
                .retrieve()
                .body(Object.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getMap(String path, Map<String, ?> query, Object... pathVariables) {
        return (Map<String, Object>) get(path, query, pathVariables);
    }

    @SuppressWarnings("unchecked")
    public List<Object> getList(String path, Object... pathVariables) {
        return (List<Object>) get(path, Map.of(), pathVariables);
    }

    /** A list with query parameters (a filter by company...). */
    @SuppressWarnings("unchecked")
    public List<Object> getListWithQuery(String path, Map<String, ?> query, Object... pathVariables) {
        return (List<Object>) get(path, query, pathVariables);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> send(HttpMethod method, String path, Object body, Object... pathVariables) {
        RestClient.RequestBodySpec request = rest.method(method).uri(path, pathVariables);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return (Map<String, Object>) request.retrieve().body(Object.class);
    }

    /** Adds the query parameters that have a value; collections become repeated parameters. */
    private static URI build(UriBuilder builder, String path, Map<String, ?> query, Object... pathVariables) {
        builder.path(path);
        query.forEach((name, value) -> {
            if (value instanceof Collection<?> values) {
                values.forEach(v -> builder.queryParam(name, v));
            } else if (value != null) {
                builder.queryParam(name, value);
            }
        });
        return builder.build(pathVariables);
    }
}

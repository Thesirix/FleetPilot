package com.fleetpilot.gateway;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A real HTTP server standing in for the vehicle service: it answers canned JSON per path and records every request
 * (path, query, Authorization header) so the tests can check what the gateway sent.
 */
final class StubVehicleService {

    record Call(String method, String path, String query, String authorization, String body) {
    }

    private record Answer(int status, String contentType, String body) {
    }

    private final HttpServer server;
    private final Map<String, Answer> answers = new ConcurrentHashMap<>();
    private final List<Call> calls = new CopyOnWriteArrayList<>();

    StubVehicleService() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    void reply(String method, String path, String json) {
        answers.put(method + " " + path, new Answer(200, "application/json", json));
    }

    void fail(String method, String path, int status, String problemJson) {
        answers.put(method + " " + path, new Answer(status, "application/problem+json", problemJson));
    }

    List<Call> calls() {
        return List.copyOf(calls);
    }

    void reset() {
        answers.clear();
        calls.clear();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        calls.add(new Call(exchange.getRequestMethod(), path, exchange.getRequestURI().getRawQuery(),
                exchange.getRequestHeaders().getFirst("Authorization"), body));
        Answer answer = answers.getOrDefault(exchange.getRequestMethod() + " " + path,
                new Answer(404, "application/problem+json", "{\"code\":\"NOT_FOUND\",\"status\":404,\"detail\":\"Not found\"}"));
        byte[] bytes = answer.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", answer.contentType());
        exchange.sendResponseHeaders(answer.status(), bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}

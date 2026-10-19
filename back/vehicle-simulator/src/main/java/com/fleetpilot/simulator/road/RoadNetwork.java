package com.fleetpilot.simulator.road;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The roads the simulated vehicles drive on: for each site of the client companies, real routes to the business
 * places around it (customers, industrial zones, ports). Computed once from OpenStreetMap with OSRM
 * ({@code tools/scripts/build-simulator-routes.py}) and shipped with the simulator: no network access at runtime.
 */
public final class RoadNetwork {

    private final Map<String, List<Route>> routesFromSite = new HashMap<>();
    private final Map<String, double[]> sites = new HashMap<>();

    public RoadNetwork(List<Route> routes, Map<String, double[]> sites) {
        this.sites.putAll(sites);
        routes.forEach(route -> routesFromSite.computeIfAbsent(route.from(), site -> new ArrayList<>()).add(route));
    }

    public static RoadNetwork load(JsonMapper mapper, String resource) {
        try (InputStream in = RoadNetwork.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing road network " + resource);
            }
            JsonNode root = mapper.readTree(in);
            Map<String, double[]> sites = new HashMap<>();
            root.path("sites").forEach(site -> sites.put(site.path("name").asString(),
                    new double[] {site.path("lat").asDouble(), site.path("lon").asDouble()}));
            List<Route> routes = new ArrayList<>();
            root.path("routes").forEach(route -> {
                List<double[]> points = new ArrayList<>();
                route.path("points").forEach(p -> points.add(new double[] {p.get(0).asDouble(), p.get(1).asDouble()}));
                routes.add(new Route(route.path("from").asString(), route.path("to").asString(), points,
                        route.path("duration").asDouble()));
            });
            return new RoadNetwork(routes, sites);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Routes leaving a site; empty when the site has no road (the vehicle is not simulated). */
    public List<Route> routesFrom(String site) {
        return routesFromSite.getOrDefault(site, List.of());
    }

    public double[] site(String name) {
        return sites.get(name);
    }

    public Set<String> sites() {
        return sites.keySet();
    }

    public int routeCount() {
        return routesFromSite.values().stream().mapToInt(List::size).sum();
    }
}

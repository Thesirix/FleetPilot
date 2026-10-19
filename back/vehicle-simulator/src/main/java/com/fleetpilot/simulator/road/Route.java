package com.fleetpilot.simulator.road;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A driving route on real roads: a polyline with the length of each point from the start, so that a position at any
 * distance along the route is found by interpolation. Lengths come from the haversine formula.
 */
public final class Route {

    private static final double EARTH_RADIUS_M = 6_371_000;

    /** A point of the road and where the vehicle heads to from it. */
    public record Point(double latitude, double longitude, int heading) {
    }

    private final String from;
    private final String to;
    private final List<double[]> points;
    private final double[] cumulative;
    /** Average speed estimated by the routing engine, in km/h. */
    private final double averageSpeedKmh;

    public Route(String from, String to, List<double[]> points, double durationSeconds) {
        if (points.size() < 2) {
            throw new IllegalArgumentException("A route needs at least two points");
        }
        this.from = from;
        this.to = to;
        this.points = List.copyOf(points);
        this.cumulative = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            cumulative[i] = cumulative[i - 1] + distance(points.get(i - 1), points.get(i));
        }
        this.averageSpeedKmh = durationSeconds <= 0 ? 40 : length() / durationSeconds * 3.6;
    }

    /** The same road driven the other way (the way back to the site). */
    public Route reversed() {
        List<double[]> back = new ArrayList<>(points);
        Collections.reverse(back);
        return new Route(to, from, back, length() / (averageSpeedKmh / 3.6));
    }

    /** Position and heading after {@code metres} along the route (clamped to the route). */
    public Point at(double metres) {
        double d = Math.clamp(metres, 0, length());
        int i = 1;
        while (i < cumulative.length - 1 && cumulative[i] < d) {
            i++;
        }
        double[] a = points.get(i - 1);
        double[] b = points.get(i);
        double segment = cumulative[i] - cumulative[i - 1];
        double ratio = segment == 0 ? 0 : (d - cumulative[i - 1]) / segment;
        return new Point(a[0] + (b[0] - a[0]) * ratio, a[1] + (b[1] - a[1]) * ratio, bearing(a, b));
    }

    public double length() {
        return cumulative[cumulative.length - 1];
    }

    public String from() {
        return from;
    }

    public String to() {
        return to;
    }

    public double averageSpeedKmh() {
        return averageSpeedKmh;
    }

    static double distance(double[] a, double[] b) {
        double lat1 = Math.toRadians(a[0]);
        double lat2 = Math.toRadians(b[0]);
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b[1] - a[1]);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(h));
    }

    /** Compass heading from a to b, 0 = north, 90 = east. */
    static int bearing(double[] a, double[] b) {
        double lat1 = Math.toRadians(a[0]);
        double lat2 = Math.toRadians(b[0]);
        double dLon = Math.toRadians(b[1] - a[1]);
        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        return (int) Math.round((Math.toDegrees(Math.atan2(y, x)) + 360) % 360);
    }
}

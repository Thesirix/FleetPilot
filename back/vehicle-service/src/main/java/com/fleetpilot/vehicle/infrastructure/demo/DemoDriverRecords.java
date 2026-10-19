package com.fleetpilot.vehicle.infrastructure.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What the drivers of the demo fleet sent from the app over the last 75 days: fill-ups and charges typed from the
 * receipts, and monthly inspections, some of them with a defect. Built from the fleet as it is in the database (the
 * vehicles driven today, their energy, their site and their real mileage), so the odometers of the receipts follow
 * the mileage history of each vehicle (between two readings, or after the last one). As with the real app, the odometer typed in the
 * latest fill-up or inspection becomes the reading of the vehicle when it is more recent. Two drivers out of three use
 * the app, the drivers with a demo login always.
 *
 * <p>Deterministic: the random generator of each vehicle is seeded with its id, so every installation shows the same
 * receipts relative to its installation day.
 */
class DemoDriverRecords {

    private static final Logger LOG = LoggerFactory.getLogger(DemoDriverRecords.class);
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    static final int DAYS = 75;

    /** Litres (or kg of CNG) per 100 km, and the usual fill-up. */
    private record Consumption(double per100, double typicalFill) {
    }

    private static final Map<String, Consumption> CONSUMPTION = Map.of(
            "CAR", new Consumption(6.2, 42),
            "VAN", new Consumption(8.4, 55),
            "TRUCK", new Consumption(27, 180));

    /** Price range per litre (kg for CNG, kWh for electricity) in euros, autumn 2026. */
    private static final Map<String, double[]> PRICES = Map.of(
            "DIESEL", new double[] {1.71, 1.86},
            "PETROL", new double[] {1.79, 1.94},
            "HYBRID", new double[] {1.79, 1.94},
            "PLUG_IN_HYBRID", new double[] {1.79, 1.94},
            "LPG", new double[] {0.96, 1.06},
            "CNG", new double[] {1.55, 1.85});

    private static final String[] BRANDS = {"TotalEnergies", "Esso", "BP", "Shell", "Intermarché", "E.Leclerc",
        "Carrefour", "Avia", "Super U", "Auchan"};
    private static final String[] CHARGERS = {"Ionity", "TotalEnergies", "Electra", "Tesla Supercharger", "Fastned",
        "Izivia"};

    private static final String[][] DEFECTS = {
        {"BODYWORK", "DAMAGED", "Rayure sur la portière arrière droite, environ 20 cm."},
        {"BODYWORK", "DAMAGED", "Petite bosse sur le pare-chocs arrière, sans peinture écaillée."},
        {"WINDSCREEN", "DAMAGED", "Impact de gravillon en bas à droite du pare-brise."},
        {"TYRES", "DAMAGED", "Pneu avant gauche usé sur le flanc intérieur."},
        {"TYRES", "DAMAGED", "Pression basse sur la roue arrière droite, à faire contrôler."},
        {"LIGHTS", "DAMAGED", "Feu stop gauche qui ne s'allume plus."},
        {"INTERIOR", "DAMAGED", "Tache sur le siège passager."},
        {"CLEANLINESS", "DAMAGED", "Véhicule rendu sale par le conducteur précédent."},
        {"SAFETY_KIT", "MISSING", "Triangle de signalisation absent du coffre."},
        {"DOCUMENTS", "MISSING", "Carte grise absente de la boîte à gants."},
    };

    private static final String[] ITEMS = {"BODYWORK", "WINDSCREEN", "TYRES", "LIGHTS", "INTERIOR", "CLEANLINESS",
        "SAFETY_KIT", "DOCUMENTS"};

    /** A mileage reading of the vehicle. */
    private record Point(Instant at, int mileage) {
    }

    private record Driven(UUID vehicleId, UUID driverId, String energy, String category, String city,
            List<Point> history, double kmPerDay, boolean featured) {

        Instant lastReadAt() {
            return history.getLast().at();
        }

        /**
         * The odometer at a moment: between two readings of the vehicle, in proportion of the time; after the last one
         * (or before the first one), at the usual daily distance. So a receipt never contradicts the mileage history.
         */
        int mileageAt(Instant at, double kmPerDay) {
            Point before = null;
            Point after = null;
            for (Point point : history) {
                if (!point.at().isAfter(at)) {
                    before = point;
                } else {
                    after = point;
                    break;
                }
            }
            if (after == null) {
                return (int) Math.round(before.mileage() + kmPerDay * days(before.at(), at));
            }
            if (before == null) {
                return (int) Math.max(0, Math.round(after.mileage() - kmPerDay * days(at, after.at())));
            }
            double share = days(before.at(), at) / Math.max(days(before.at(), after.at()), 1e-6);
            int low = Math.min(before.mileage(), after.mileage());
            int high = Math.max(before.mileage(), after.mileage());
            long mileage = Math.round(before.mileage() + (after.mileage() - before.mileage()) * share);
            return Math.clamp(mileage, low, high);
        }

        private static double days(Instant from, Instant to) {
            return Duration.between(from, to).toMinutes() / 1440.0;
        }
    }

    /** The latest odometer a driver typed after the last reading of the vehicle. */
    private record Latest(Instant at, int mileage) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;

    DemoDriverRecords(JdbcTemplate jdbc, TransactionTemplate transactions, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** Only into a fleet without any driver record yet: what real drivers sent is never mixed with the demo. */
    void loadIfEmpty() {
        Long existing = jdbc.queryForObject("select (select count(*) from fuel_entry) + (select count(*) from inspection)",
                Long.class);
        if (existing != null && existing > 0) {
            return;
        }
        Instant now = clock.instant();
        List<Object[]> fuel = new ArrayList<>();
        List<Object[]> inspections = new ArrayList<>();
        List<Object[]> checks = new ArrayList<>();
        List<Object[]> readings = new ArrayList<>();
        for (Driven vehicle : drivenVehicles(now)) {
            Random random = new Random(vehicle.vehicleId().getMostSignificantBits() ^ 0x5eed);
            // The drivers with a demo login always use the app
            if (random.nextInt(3) == 0 && !vehicle.featured()) {
                continue;
            }
            // One daily distance for the receipts and the inspections, so their odometers never go backwards
            double kmPerDay = vehicle.kmPerDay() > 5 ? vehicle.kmPerDay() : 25 + random.nextInt(50);
            Latest latest = fillUps(vehicle, kmPerDay, random, now, fuel);
            Latest inspected = inspections(vehicle, kmPerDay, random, now, inspections, checks);
            if (inspected != null && (latest == null || inspected.at().isAfter(latest.at()))) {
                latest = inspected;
            }
            if (latest != null) {
                readings.add(new Object[] {id("reading", vehicle.vehicleId(), 0), vehicle.vehicleId(), latest.mileage(),
                        Timestamp.from(latest.at())});
            }
        }
        transactions.executeWithoutResult(status -> {
            jdbc.batchUpdate("insert into fuel_entry (id, vehicle_id, driver_id, filled_at, kind, quantity, amount, "
                    + "mileage, station, payment, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", fuel);
            jdbc.batchUpdate("insert into inspection (id, vehicle_id, driver_id, kind, performed_at, mileage, fuel_level, "
                    + "remarks, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?)", inspections);
            jdbc.batchUpdate("insert into inspection_item (inspection_id, item, condition, comment) values (?, ?, ?, ?)",
                    checks);
            jdbc.batchUpdate("insert into mileage_reading (id, vehicle_id, mileage, read_at, source) "
                    + "values (?, ?, ?, ?, 'DRIVER')", readings);
            jdbc.batchUpdate("update vehicle set mileage = ?, mileage_read_at = ? where id = ?",
                    readings.stream().map(row -> new Object[] {row[2], row[3], row[1]}).toList());
        });
        LOG.info("Demo driver records loaded: {} fill-ups and charges, {} inspections", fuel.size(), inspections.size());
    }

    /** Vehicles in service with a driver today, and how much they drove per day over the last six months. */
    private List<Driven> drivenVehicles(Instant now) {
        Timestamp since = Timestamp.from(now.minus(180, ChronoUnit.DAYS));
        Map<UUID, List<Point>> histories = new HashMap<>();
        jdbc.query("select vehicle_id, read_at, mileage from mileage_reading order by vehicle_id, read_at",
                rs -> {
                    histories.computeIfAbsent(rs.getObject(1, UUID.class), id -> new ArrayList<>())
                            .add(new Point(rs.getTimestamp(2).toInstant(), rs.getInt(3)));
                });
        return jdbc.query("""
                select v.id, a.driver_id, v.energy, v.category, s.city, v.mileage, v.mileage_read_at, d.employee_number,
                       (select (max(r.mileage) - min(r.mileage))
                               / greatest(extract(epoch from max(r.read_at) - min(r.read_at)) / 86400, 1)
                          from mileage_reading r where r.vehicle_id = v.id and r.read_at >= ?) as km_per_day
                from vehicle v
                join assignment a on a.vehicle_id = v.id and a.end_date is null
                join site s on s.id = v.site_id
                join driver d on d.id = a.driver_id
                where v.status = 'ACTIVE'
                order by v.id""",
                (rs, n) -> {
                    UUID id = rs.getObject(1, UUID.class);
                    // A vehicle without any reading yet: its odometer of today is the only point
                    List<Point> history = histories.getOrDefault(id, List.of(new Point(
                            rs.getTimestamp(7) == null ? now : rs.getTimestamp(7).toInstant(), rs.getInt(6))));
                    return new Driven(id, rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4),
                            rs.getString(5), history, rs.getObject(9) == null ? 0 : Math.min(rs.getDouble(9), 600),
                            DemoCatalog.isFeaturedEmployee(rs.getString(8)));
                },
                since);
    }

    private Latest fillUps(Driven v, double kmPerDay, Random random, Instant now, List<Object[]> rows) {
        Latest latest = null;
        boolean electric = "ELECTRIC".equals(v.energy());
        boolean plugIn = "PLUG_IN_HYBRID".equals(v.energy());
        Consumption consumption = CONSUMPTION.getOrDefault(v.category(), CONSUMPTION.get("CAR"));
        // Electric vehicles mostly charge at the depot: the app only sees the public charges on the road
        double kmBetween = electric ? 260 + random.nextInt(220)
                : consumption.typicalFill() / consumption.per100() * 100 * (0.75 + random.nextDouble() * 0.2);
        double day = random.nextDouble() * Math.min(kmBetween / kmPerDay, DAYS);
        String station = (electric ? CHARGERS[random.nextInt(CHARGERS.length)] : BRANDS[random.nextInt(BRANDS.length)])
                + " " + v.city();
        while (day < DAYS) {
            double daysAgo = DAYS - day;
            Instant at = workingHour(now.minus(Math.round(daysAgo * 24 * 60), ChronoUnit.MINUTES), random);
            if (at.isBefore(now)) {
                int mileage = v.mileageAt(at, kmPerDay);
                if (at.isAfter(v.lastReadAt())) {
                    latest = new Latest(at, mileage);
                }
                boolean charge = electric || plugIn && random.nextInt(3) == 0;
                double quantity;
                double unitPrice;
                if (charge) {
                    quantity = electric ? 22 + random.nextDouble() * 38 : 6 + random.nextDouble() * 4;
                    unitPrice = 0.29 + random.nextDouble() * 0.30;
                } else {
                    quantity = consumption.typicalFill() * (0.7 + random.nextDouble() * 0.45);
                    double[] range = PRICES.getOrDefault(v.energy(), PRICES.get("DIESEL"));
                    unitPrice = range[0] + random.nextDouble() * (range[1] - range[0]);
                }
                BigDecimal q = BigDecimal.valueOf(quantity).setScale(2, RoundingMode.HALF_UP);
                BigDecimal amount = q.multiply(BigDecimal.valueOf(unitPrice)).setScale(2, RoundingMode.HALF_UP);
                // One station near the site most of the time, another one on the road now and then
                String where = random.nextInt(4) == 0
                        ? (charge ? CHARGERS[random.nextInt(CHARGERS.length)] : BRANDS[random.nextInt(BRANDS.length)])
                                + " " + v.city()
                        : station;
                String payment = random.nextInt(12) == 0 ? "PERSONAL" : "FUEL_CARD";
                rows.add(new Object[] {id("fuel", v.vehicleId(), rows.size()), v.vehicleId(), v.driverId(),
                        Timestamp.from(at), charge ? "CHARGE" : "FUEL", q, amount, mileage, where, payment,
                        Timestamp.from(at.plus(2 + random.nextInt(40), ChronoUnit.MINUTES))});
            }
            day += kmBetween / kmPerDay * (0.8 + random.nextDouble() * 0.4);
        }
        return latest;
    }

    private Latest inspections(Driven v, double kmPerDay, Random random, Instant now, List<Object[]> inspections,
            List<Object[]> checks) {
        Latest latest = null;
        for (int month = 2; month >= 0; month--) {
            int daysAgo = month * 30 + 2 + random.nextInt(6);
            if (daysAgo >= DAYS || random.nextInt(5) == 0) {
                continue;
            }
            Instant at = workingHour(now.minus(daysAgo, ChronoUnit.DAYS), random);
            UUID id = id("inspection", v.vehicleId(), month);
            int mileage = v.mileageAt(at, kmPerDay);
            if (at.isAfter(v.lastReadAt()) && at.isBefore(now)) {
                latest = new Latest(at, mileage);
            }
            String[] defect = random.nextInt(7) == 0 ? DEFECTS[random.nextInt(DEFECTS.length)] : null;
            inspections.add(new Object[] {id, v.vehicleId(), v.driverId(), "PERIODIC", Timestamp.from(at), mileage,
                    5 * (4 + random.nextInt(17)), null, Timestamp.from(at.plus(3, ChronoUnit.MINUTES))});
            for (String item : ITEMS) {
                boolean broken = defect != null && defect[0].equals(item);
                checks.add(new Object[] {id, item, broken ? defect[1] : "OK", broken ? defect[2] : null});
            }
        }
        return latest;
    }

    /** Fill-ups and inspections happen between 7 am and 7 pm, Paris time. */
    private static Instant workingHour(Instant day, Random random) {
        LocalDate date = LocalDate.ofInstant(day, PARIS);
        return date.atTime(7 + random.nextInt(12), random.nextInt(60)).atZone(PARIS).toInstant();
    }

    private static UUID id(String kind, UUID vehicleId, int index) {
        return UUID.nameUUIDFromBytes(("fleetpilot-demo:" + kind + ":" + vehicleId + ":" + index).getBytes(StandardCharsets.UTF_8));
    }
}

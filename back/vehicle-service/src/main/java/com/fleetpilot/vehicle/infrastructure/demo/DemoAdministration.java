package com.fleetpilot.vehicle.infrastructure.demo;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What the administration shows on a new installation: the head office and the contact of the three demo companies,
 * and an activity journal of the last 45 days rebuilt from the demo records (deadlines done, claims declared and
 * assessed, vehicles assigned, readings, contracts), written by the FleetPilot fleet managers and the client managers.
 * Only into an empty journal; deterministic like the rest of the demo.
 */
class DemoAdministration {

    private static final Logger LOG = LoggerFactory.getLogger(DemoAdministration.class);
    private static final int DAYS = 45;

    private static final String NEOLIA = "474a1f7d-c59a-3321-a0a2-729ec42501d5";
    private static final String METROPOLE = "29d53fba-827a-3e2b-bec1-e74f22fc6de9";
    private static final String CALANQUES = "849d0af0-f20c-3127-96a3-7d8e0c5a30f3";

    /** The author of an action: a FleetPilot fleet manager, or the client manager of the company, picked from the record id. */
    private static final String ACTOR = """
            case when abs(hashtext(%1$s)) %% 3 = 0 then
                     case %2$s::text when '%3$s' then 'Mathilde Roux' when '%4$s' then 'Pascal Bianchi'
                                     when '%5$s' then 'Nadia Haddad' else 'Claire Morel' end
                 when abs(hashtext(%1$s)) %% 3 = 1 then 'Claire Morel'
                 when abs(hashtext(%1$s)) %% 5 = 2 then 'Sophie Garnier'
                 else 'Yanis Cherif' end""";
    private static final String ROLE = """
            case when abs(hashtext(%1$s)) %% 3 = 0 and %2$s::text in ('%3$s', '%4$s', '%5$s') then 'client-manager'
                 else 'fleet-manager' end""";

    private final JdbcTemplate jdbc;

    DemoAdministration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void loadIfEmpty(Instant now) {
        jdbc.update("""
                update company set address = '12 rue de la Victoire', postal_code = '75009', contact_name = 'Mathilde Roux',
                    contact_email = 'm.roux@neolia-energies.fr', contact_phone = '01 44 63 20 10'
                where id = ?::uuid and address is null""", NEOLIA);
        jdbc.update("""
                update company set address = '2 quai d''Arenc', postal_code = '13002', contact_name = 'Pascal Bianchi',
                    contact_email = 'p.bianchi@azur-provence-metropole.fr', contact_phone = '04 91 99 70 40'
                where id = ?::uuid and address is null""", METROPOLE);
        jdbc.update("""
                update company set address = '9 rue de l''Europe, ZI Les Estroublans', postal_code = '13127',
                    contact_name = 'Nadia Haddad', contact_email = 'n.haddad@calanques-logistique.fr',
                    contact_phone = '04 42 15 38 60'
                where id = ?::uuid and address is null""", CALANQUES);

        Long existing = jdbc.queryForObject("select count(*) from audit_entry", Long.class);
        if (existing != null && existing > 0) {
            return;
        }
        Timestamp since = Timestamp.from(now.minus(DAYS, ChronoUnit.DAYS));
        Timestamp until = Timestamp.from(now);
        int rows = 0;
        for (Kind kind : KINDS) {
            rows += insert(kind, since, until);
        }
        LOG.info("Demo activity journal loaded: {} actions over {} days", rows, DAYS);
    }

    /**
     * One kind of journal line: the records it is built from, what it shows and when it happened. In {@code actor}
     * and {@code role}, "%s" stands for the default author and role (a FleetPilot fleet manager or the client manager).
     */
    private record Kind(String action, String targetType, String idExpression, String label, String details,
            String occurredAt, String fromWhere, String actor, String role) {

        Kind(String action, String targetType, String idExpression, String label, String details, String occurredAt,
                String fromWhere) {
            this(action, targetType, idExpression, label, details, occurredAt, fromWhere, "%s", "%s");
        }
    }

    private static final List<Kind> KINDS = List.of(
            new Kind("DEADLINE_COMPLETED", "DEADLINE", "d.id", "v.registration",
                    "d.type || ', ' || d.completed_on || ', ' || d.completed_mileage || ' km'",
                    "(d.completed_on + time '08:30' + (abs(hashtext(d.id::text)) % 540) * interval '1 minute')"
                            + " at time zone 'Europe/Paris'",
                    "deadline d join vehicle v on v.id = d.vehicle_id where d.status = 'DONE'"),
            // A claim with a driver was declared by the driver, from the app; the others by a fleet manager
            new Kind("INCIDENT_DECLARED", "INCIDENT", "i.id", "i.reference", "i.type || ', ' || v.registration",
                    "i.occurred_at + (abs(hashtext(i.id::text)) % 180) * interval '1 minute'",
                    "incident i join vehicle v on v.id = i.vehicle_id left join driver dr on dr.id = i.driver_id where true",
                    "coalesce(dr.first_name || ' ' || dr.last_name, %s)",
                    "case when dr.id is null then %s else 'driver' end"),
            new Kind("INCIDENT_ASSESSED", "INCIDENT", "i.id", "i.reference",
                    "i.responsibility || coalesce(', ' || round(i.estimated_cost) || ' EUR', '')",
                    "i.occurred_at + interval '1 day' + (abs(hashtext(i.reference)) % 420) * interval '1 minute'",
                    "incident i join vehicle v on v.id = i.vehicle_id where i.responsibility <> 'UNKNOWN'"),
            new Kind("DRIVER_ASSIGNED", "VEHICLE", "v.id", "v.registration",
                    "dr.first_name || ' ' || dr.last_name || ' (' || a.kind || ')'",
                    "(a.start_date + time '09:00' + (abs(hashtext(a.id::text)) % 360) * interval '1 minute')"
                            + " at time zone 'Europe/Paris'",
                    "assignment a join vehicle v on v.id = a.vehicle_id join driver dr on dr.id = a.driver_id where true"),
            new Kind("MILEAGE_RECORDED", "VEHICLE", "v.id", "v.registration", "r.mileage || ' km (MANAGER)'",
                    "r.read_at",
                    "mileage_reading r join vehicle v on v.id = r.vehicle_id where r.source = 'MANAGER'"),
            new Kind("CONTRACT_CREATED", "CONTRACT", "l.id", "l.contract_number", "l.lessor || ', ' || v.registration",
                    "(l.start_date - 3 + time '10:00' + (abs(hashtext(l.id::text)) % 300) * interval '1 minute')"
                            + " at time zone 'Europe/Paris'",
                    "lease_contract l join vehicle v on v.id = l.vehicle_id where true"));

    /** One journal line per record of the kind that happened in the period. */
    private int insert(Kind kind, Timestamp since, Timestamp until) {
        String key = kind.idExpression() + "::text";
        String sql = """
                insert into audit_entry (id, occurred_at, actor, actor_role, company_id, action, target_type, target_id,
                                         target_label, details)
                select md5('%1$s:' || %2$s)::uuid, %3$s, %4$s, %5$s, v.company_id, '%1$s', '%6$s', %2$s, %7$s, %8$s
                from %9$s and %3$s between ? and ?
                """.formatted(kind.action(), key, kind.occurredAt(),
                kind.actor().replace("%s", "(" + ACTOR.formatted(key, "v.company_id", NEOLIA, METROPOLE, CALANQUES) + ")"),
                kind.role().replace("%s", "(" + ROLE.formatted(key, "v.company_id", NEOLIA, METROPOLE, CALANQUES) + ")"),
                kind.targetType(), kind.label(), kind.details(), kind.fromWhere());
        return jdbc.update(sql, since, until);
    }
}

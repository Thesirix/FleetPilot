#!/usr/bin/env sh
# Removes from the demo platform what the end-to-end suites (Cypress, Playwright, Selenium, Espresso) create, so that
# the demo and the screenshots stay realistic. The suites mark what they create: "Cypress" in a name, "CY-" in a code
# or a reference, an e-mail cy-...@example.test, a VIN starting with VF1CY, a station "Station test ...".
#   - fleet: test vehicles, drivers and companies with what is attached to them, test claims, fill-ups, inspections and
#     the odometer readings they recorded, the reading Cypress types as a driver (37 km above the previous one), the
#     readings typed in the lessor extranet (the demo data has none), the deadlines Cypress marked as done;
#   - quotes: the quotes and garages of the tests, the analysis rules back to their defaults;
#   - energy: the electric models and low emission zones of the tests;
#   - accounts: the Keycloak accounts cy-...@example.test;
#   - reports: every report requested (the demo has none), and what followed everywhere: insurer files, partner
#     exchanges, e-mails and lines of the activity journal.
# Each vehicle touched gets back the odometer of its latest remaining reading. Running it twice is harmless.
#   sh tools/scripts/clean-test-data.sh
set -e
export MSYS_NO_PATHCONV=1

psql() {
  db=$1
  shift
  docker compose exec -T postgres psql -U fleetpilot -d "$db" -v ON_ERROR_STOP=1 -qtA "$@"
}
quoted() {
  psql "$1" -c "select coalesce(string_agg(quote_literal($2), ','), '''''') from $3"
}

# ------------------------------------------------------------------ what the tests created in the fleet
vehicles=$(quoted fleet_vehicle "id::text" "vehicle where vin like 'VF1CY%'")
plates=$(quoted fleet_vehicle "registration" "vehicle where vin like 'VF1CY%'")
drivers=$(quoted fleet_vehicle "id::text" "driver where email like 'cy-%@example.test'")
companies=$(quoted fleet_vehicle "id::text" "company where name like 'Cypress %'")
refs=$(quoted fleet_vehicle "reference" "incident where description like 'Rétroviseur arraché sur le parking, test %'")

psql fleet_vehicle -c "
  begin;
  create temp table test_inspection as select distinct inspection_id as id from inspection_item
    where comment = 'Pneu avant droit usé sur le flanc';
  create temp table test_reading as
    select r.id, r.vehicle_id, r.mileage, r.source from mileage_reading r
     where r.source = 'LESSOR'
        or r.vehicle_id::text in ($vehicles)
        or exists (select 1 from fuel_entry f where f.station like 'Station test %' and f.vehicle_id = r.vehicle_id
                   and f.filled_at = r.read_at and f.mileage = r.mileage)
        or exists (select 1 from inspection i join test_inspection t on t.id = i.id where i.vehicle_id = r.vehicle_id
                   and i.performed_at = r.read_at and i.mileage = r.mileage)
    union
    select id, vehicle_id, mileage, source from (select id, vehicle_id, source, read_at, mileage,
                                       mileage - lag(mileage) over (partition by vehicle_id order by read_at) as added
                                  from mileage_reading) typed
     where source = 'DRIVER' and added = 37 and read_at <> date_trunc('second', read_at);
  -- Deadlines marked as done by Cypress: the next one it scheduled is removed and the done one is open again (the
  -- demo deadlines are all created in the same transaction, at the first start)
  create temp table scheduled as select id, vehicle_id, type, created_at::date as day from deadline
    where status = 'OPEN' and created_at > (select min(created_at) from deadline)
      and vehicle_id::text not in ($vehicles);
  create temp table reopened as select d.id from deadline d join scheduled s
    on d.status = 'DONE' and d.vehicle_id = s.vehicle_id and d.type = s.type and d.completed_on = s.day;

  -- The journal lines about what goes away
  delete from audit_entry where target_id in ($vehicles) or target_id in ($drivers) or target_id in ($companies)
    or company_id::text in ($companies) or target_label in ($refs) or target_label like 'CY-%'
    or target_label like 'Cypress %' or target_label like 'cy-%@example.test' or details like '%Cypress%'
    or target_id in (select id::text from reopened)
    or (action = 'MILEAGE_RECORDED' and details like '% km (LESSOR)')
    or exists (select 1 from test_reading t where t.source = 'DRIVER' and audit_entry.action = 'MILEAGE_RECORDED'
               and audit_entry.target_id = t.vehicle_id::text and audit_entry.details = t.mileage || ' km (DRIVER)');

  delete from mileage_reading where id in (select id from test_reading);
  delete from photo where owner_type = 'INCIDENT' and owner_id in
    (select id from incident where reference in ($refs) or vehicle_id::text in ($vehicles));
  delete from incident where reference in ($refs) or vehicle_id::text in ($vehicles) or driver_id::text in ($drivers);
  delete from fuel_entry where station like 'Station test %' or vehicle_id::text in ($vehicles);
  delete from photo where owner_type = 'INSPECTION' and owner_id in
    (select id from test_inspection union select id from inspection where vehicle_id::text in ($vehicles));
  delete from inspection where id in (select id from test_inspection) or vehicle_id::text in ($vehicles);
  delete from photo where vehicle_id::text in ($vehicles);
  delete from deadline where vehicle_id::text in ($vehicles);
  delete from assignment where vehicle_id::text in ($vehicles) or driver_id::text in ($drivers);
  delete from lease_contract where vehicle_id::text in ($vehicles) or contract_number like 'CY-%';
  delete from vehicle where id::text in ($vehicles);
  delete from driver where id::text in ($drivers);
  delete from site where company_id::text in ($companies);
  delete from cost_center where company_id::text in ($companies);
  delete from company where id::text in ($companies);
  delete from deadline where id in (select id from scheduled);
  update deadline d set status = 'OPEN', completed_on = null, completed_mileage = null where d.id in (select id from reopened);
  update vehicle v set mileage = r.mileage, mileage_read_at = r.read_at
    from (select distinct on (vehicle_id) vehicle_id, mileage, read_at from mileage_reading
          where vehicle_id in (select vehicle_id from test_reading) order by vehicle_id, read_at desc) r
    where v.id = r.vehicle_id;
  commit;"

# ------------------------------------------------------------------ quote-service
quotes=$(quoted fleet_quote "reference" "quote where garage_reference like 'CY-%' or vehicle_id::text in ($vehicles)
  or garage_id in (select id from garage where name like 'Garage Cypress %')")
psql fleet_quote -c "
  delete from quote where reference in ($quotes);
  delete from garage where name like 'Garage Cypress %';
  delete from vehicle_ref where id::text in ($vehicles);
  update analysis_rules set part_tolerance = 0.10, time_tolerance = 0.15, rate_tolerance = 0.10, supplies_share = 0.06,
    supplies_minimum = 15.00, negotiation_share = 0.01, negotiation_minimum = 5.00, auto_approval_limit = 300.00,
    manager_approval_limit = 5000.00, updated_by = 'FleetPilot'
  where auto_approval_limit <> 300.00 or manager_approval_limit <> 5000.00 or part_tolerance <> 0.10
     or time_tolerance <> 0.15 or rate_tolerance <> 0.10 or supplies_share <> 0.06 or supplies_minimum <> 15.00
     or negotiation_share <> 0.01 or negotiation_minimum <> 5.00;"

# ------------------------------------------------------------------ other services holding a copy of the vehicles
psql fleet_telemetry -c "delete from vehicle_reference where vehicle_id::text in ($vehicles)"
psql fleet_energy -c "
  delete from assessment where vehicle_id::text in ($vehicles);
  update assessment set ev_model_code = null where ev_model_code like 'CY-%';
  delete from ev_model where code like 'CY-%';
  delete from zfe_rule where city like 'Cypressville%';"
psql fleet_reporting -c "delete from report_file; delete from report;"

# ------------------------------------------------------------------ integration-esb
# The claims still in the fleet, copied into a session table of the integration database. The exchanges of a claim
# carry its reference (declaration) or the number of the insurer's file (follow-up).
claims=$(psql fleet_vehicle -c "copy (select reference from incident) to stdout" | psql fleet_integration \
  -c "create temp table fleet_claim (reference varchar(30) primary key)" \
  -c "copy fleet_claim from stdin" \
  -c "create temp table kept_claim as select reference as source from fleet_claim
        union select claim_number from insurer_claim where incident_reference in (select reference from fleet_claim)" \
  -c "select coalesce(string_agg(distinct quote_literal(incident_reference), ','), '''''') from insurer_claim
        where incident_reference not in (select reference from fleet_claim)" \
  -c "delete from exchange where flow = 'INSURER_CLAIMS' and source not in (select source from kept_claim)" \
  -c "delete from insurer_claim where incident_reference not in (select reference from fleet_claim)" \
  -c "delete from simulated_insurer_claim where incident_ref not in (select reference from fleet_claim)" \
  | tail -1)
psql fleet_integration -c "delete from exchange where flow = 'GARAGE_QUOTES' and source like '%devis-CY-%'"

# ------------------------------------------------------------------ notification-service
removed="$refs,$claims,$quotes,$plates"
psql fleet_notification -c "delete from notification where template = 'report-ready' or recipient like 'cy-%@example.test'
  or exists (select 1 from unnest(array[$removed]::text[]) r where r <> '' and subject like '%' || r || '%')"

# ------------------------------------------------------------------ Keycloak accounts of the tests
docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 \
  --realm master --user admin --password admin --config /tmp/kcadm.config >/dev/null 2>&1
accounts=$(docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh get users -r fleetpilot -q search=cy- --fields id,email \
  --format csv --noquotes --config /tmp/kcadm.config 2>/dev/null | grep '@example.test' | cut -d, -f1 || true)
for account in $accounts; do
  docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh delete "users/$account" -r fleetpilot \
    --config /tmp/kcadm.config >/dev/null 2>&1
done

list() {
  echo "$*" | tr ',' '\n' | tr -d "' " | grep -v '^$' | sort -u | paste -sd ' ' - | sed 's/^$/none/'
}
count() {
  echo "$*" | tr ',' '\n' | tr -d "' " | grep -cv '^$' || true
}
echo "Test data removed. Vehicles: $(list "$plates"). Claims: $(list "$refs,$claims"). Quotes: $(list "$quotes")."
echo "Drivers: $(count "$drivers"), companies: $(count "$companies"), accounts: $(echo "$accounts" | grep -c . || true)."

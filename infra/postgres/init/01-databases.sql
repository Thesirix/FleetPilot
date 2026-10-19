-- One database per service: each service owns its data and its schema migrations (Flyway).
-- Executed once, by the official PostgreSQL image, when the data volume is created.

create database fleet_vehicle owner fleetpilot;
create database fleet_quote owner fleetpilot;
create database fleet_telemetry owner fleetpilot;
create database fleet_energy owner fleetpilot;
create database fleet_notification owner fleetpilot;
create database fleet_reporting owner fleetpilot;
create database fleet_integration owner fleetpilot;
create database keycloak owner fleetpilot;
create database sonarqube owner fleetpilot;

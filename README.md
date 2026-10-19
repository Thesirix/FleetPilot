<div align="center">

<img src="front/public/logo.svg" alt="FleetPilot" width="84" />

# FleetPilot

### The fleet management platform for companies and local authorities.

**Vehicles, drivers, leases, deadlines, claims, garage quotes, connected vehicles and the move to electric, in one
platform.**

[![Java 25](https://img.shields.io/badge/Java-25-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](#tech-stack)
[![Spring Boot 4](https://img.shields.io/badge/Spring_Boot-4.1-6DB33F?style=flat-square&logo=springboot&logoColor=white)](#tech-stack)
[![Nuxt 4](https://img.shields.io/badge/Nuxt-4-00DC82?style=flat-square&logo=nuxt&logoColor=white)](#tech-stack)
[![Kafka 4](https://img.shields.io/badge/Kafka-4-231F20?style=flat-square&logo=apachekafka&logoColor=white)](#tech-stack)
[![Apache Camel](https://img.shields.io/badge/Apache_Camel-4.22-E0234E?style=flat-square&logo=apache&logoColor=white)](#tech-stack)
[![Keycloak 26](https://img.shields.io/badge/Keycloak-26-4D4D4D?style=flat-square&logo=keycloak&logoColor=white)](#tech-stack)
[![PostgreSQL 18](https://img.shields.io/badge/PostgreSQL-18-4169E1?style=flat-square&logo=postgresql&logoColor=white)](#tech-stack)
[![Android](https://img.shields.io/badge/Android-Java-3DDC84?style=flat-square&logo=android&logoColor=white)](#the-driver-app)
[![Docker](https://img.shields.io/badge/Docker-one_command-2496ED?style=flat-square&logo=docker&logoColor=white)](#quick-start)
[![Tests](https://img.shields.io/badge/tests-500%2B-2EAD33?style=flat-square&logo=junit5&logoColor=white)](#quality--testing)

[Why FleetPilot](#why-fleetpilot) · [Quick start](#quick-start) · [Product tour](#product-tour) · [Features](#feature-reference) · [Tech stack](#tech-stack) · [Architecture](#architecture) · [Testing](#quality--testing) · [FAQ](#faq)

<br /><br />

<img src="assets/login-page.gif" alt="The FleetPilot sign-in page, with its animated brand panel" width="1100" />

</div>

---

A company that runs a few hundred vehicles deals every day with the same chain of small, costly tasks: technical
inspections and services that must not be missed, lease contracts whose mileage drifts, garage quotes that are
too expensive, claims to declare to the insurer, fuel cards to check, drivers to equip, and a law that asks for
more low-emission vehicles every year. Fleet managers juggle spreadsheets, lessor portals, e-mails and phone calls.

**FleetPilot** puts the whole chain in one platform. Fleet managers run the fleets of their client companies from a
web portal; drivers report mileage, fill-ups, inspections and claims from an Android app or an installable web
app, even without network; garages send their quotes and invoices; lessors work in their extranet; connected
vehicles stream their position and fault codes. Behind it, eleven Java services, an integration bus, Kafka, an
identity provider and an analytics stack, all started with **one `docker compose up`**.

> [!NOTE]
> The screens in this README show the demonstration dataset that FleetPilot generates on its first start:
> companies, people, registration numbers and SIRET numbers are invented, vehicle models and garages are realistic. The interface
> is written in French (an English translation of the portal is included); the code and the documentation are in
> English.

## Why FleetPilot

|                                    |                                                                                                                                                                                                       |
| ---------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Nothing falls through the gaps** | Deadlines are scheduled from French rules (technical inspection at 4 years then every 2, every year for trucks, services by date or mileage) and reminded at D-30, D-7 and when overdue, exactly once. |
| **Garage quotes cost less**        | Every quote is compared line by line with a reference tariff: overpriced parts, hours above the standard time, duplicates, hourly rate. Conforming quotes are accepted on their own, the others negotiated. |
| **Real data, not declarations**    | Connected vehicles report their odometer, trips and fault codes; fuel card transactions are checked one by one; lessor invoices are matched against the contracts.                                    |
| **The move to electric, in euros** | Each vehicle is checked against its real usage over 90 days. The platform proposes an electric model, the yearly saving, the CO2 avoided and a quarterly plan that meets the LOM law quotas.           |
| **Drivers in the loop**            | A native Android app and an installable web app: fill-ups, inspections with photos, claims with GPS position, garages nearby, notifications. Everything works offline and leaves when the network is back. |
| **Partners plugged in**            | Garages send XML quotes or use their own space, lessors use their extranet, the insurer is called through its API, fuel card providers drop CSV files: one integration bus, every exchange logged and replayable. |
| **Run from the portal**            | Client companies, sites, user accounts, rules of the quote analysis and electric catalogue are administered in the portal, and an activity journal tells who did what. |
| **Secure and dependable**         | Strict isolation between client companies, single sign-on, events that are never lost, centralised logs, tests at every level and a CI pipeline with a quality gate.                                  |

## Quick start

All you need is [Docker Desktop](https://www.docker.com/products/docker-desktop/) with about **12 GB of memory**
(16 GB with the analytics stack).

```bash
git clone <repository-url> fleetpilot
cd fleetpilot
docker compose up -d --build
```

The first start builds the images, creates one database per service, imports the Keycloak realm, loads the demo
data and starts the vehicle simulator. Every service has a health check: open the portal when
`docker compose ps` shows them all `healthy` (a few minutes on the first start).

| URL                                          | What                                                                    |
| -------------------------------------------- | ----------------------------------------------------------------------- |
| http://localhost                             | **Portal**: fleet managers, garage space, driver web app                |
| http://localhost:8095/extranet               | **Lessor extranet v1** (WAR on Tomcat behind Apache httpd)              |
| http://localhost:8025                        | **Mailpit**: every e-mail sent by FleetPilot, with its PDF attachments  |
| http://localhost:8180/admin                  | Keycloak administration (`admin` / `admin`)                             |
| http://localhost:8161                        | ActiveMQ console (`admin` / `admin`)                                    |
| http://localhost:8081/swagger-ui.html        | Fleet API, interactive documentation (the same page on each service port) |
| http://localhost:5601                        | Kibana dashboards (with the `observability` profile)                    |

| Command                                          | Effect                                                                     |
| ------------------------------------------------ | -------------------------------------------------------------------------- |
| `docker compose up -d --build`                   | Core platform: databases, messaging, identity, services, portal, extranet |
| `docker compose --profile observability up -d`   | Adds Elasticsearch, Logstash, Filebeat, Kibana and the Kafka Streams pipeline |
| `docker compose --profile tools up -d`           | Adds Kafka UI on http://localhost:8091                                     |
| `docker compose --profile quality up -d`         | Adds SonarQube on http://localhost:9000                                    |
| `docker compose down`                            | Stops everything, data is kept                                             |
| `docker compose down -v`                         | Stops everything and deletes the data (the demo is generated again on the next start) |

### Sign in

There is **one sign-in page** for everyone (Keycloak, in the colours of FleetPilot): the account decides what you
see. Password of every demo account: **`FleetPilot2026!`**

| Account                                | Role                         | What it shows                                                              |
| -------------------------------------- | ---------------------------- | -------------------------------------------------------------------------- |
| `claire.morel`                         | Fleet manager (LDAP)         | The fleets of the three client companies: the account used in this README  |
| `theo.lambert`                         | Platform administrator (LDAP) | Everything, including the administration: companies, accounts, settings  |
| `m.roux@neolia-energies.fr`            | Client fleet manager         | The fleet of Néolia Énergies only                                          |
| `p.bianchi@azur-provence-metropole.fr` | Client fleet manager         | The fleet of Métropole Azur Provence only                                  |
| `n.haddad@calanques-logistique.fr`     | Client fleet manager         | The fleet of Transports Calanques Logistique only                          |
| `jean.dupont@neolia-energies.fr`       | Driver, Marseille            | The van GR-832-SJ in the driver app (Android, or http://localhost/conducteur) |
| `camille.martin@neolia-energies.fr`    | Driver, Marseille            | Another driver of the same company                                         |
| `atelier@garage-du-prado.fr`           | Garage                       | The garage space of Garage du Prado, Marseille                             |
| `devis@carrosserie-vieux-port.fr`      | Garage                       | The garage space of Carrosserie du Vieux-Port, Marseille                   |
| `extranet@arval.fr`                    | Lessor                       | The Arval contracts in the extranet v1                                     |
| `extranet@ayvens.fr`                   | Lessor                       | The Ayvens contracts in the extranet v1                                    |

The FleetPilot staff accounts (`claire.morel`, `theo.lambert`, `yanis.cherif`, `sophie.garnier`) live in an OpenLDAP
directory federated by Keycloak; their LDAP groups become their roles.

<details>
<summary><strong>What the demo data contains</strong></summary>

<br />

|                    |                                                                                                              |
| ------------------ | ------------------------------------------------------------------------------------------------------------ |
| Client companies   | 3: a national energy company, a metropolitan authority and a transport SME, 17 sites, Marseille first        |
| Vehicles           | 1,500 of real models: cars, vans, trucks and machinery, every energy, with registration, VIN and Crit'Air    |
| Drivers            | 1,200 with their licence categories and current and past assignments                                         |
| Lease contracts    | 1,113 with six lessors, and the projection of the mileage at the end of each one                             |
| Deadlines          | 7,449: technical inspections, services, tyres, tachograph, end of contract                                   |
| Claims             | 317, in every status of their lifecycle                                                                      |
| Garages and quotes | 169 garages in 8 regions, 754 quotes with their analysis, negotiation and invoice                            |
| Connected vehicles | 160 vehicles driving live on 201 real road routes, 67,000 days of usage history                              |
| Driver app         | 7,152 fill-ups and charges, 1,596 inspections, typed from the real odometer of each vehicle                  |
| Partner files      | Garage XML quotes, lessor CSV invoices, 5,593 fuel card transactions, processed by the real integration routes |
| E-mails            | About 900 e-mails already sent: reminders, purchase orders, alerts, claims                                   |

The data is generated with fixed seeds: every installation looks exactly the same, relative to the day it was
installed.

</details>

## Product tour

### Signing in

<img src="assets/login.gif" alt="Signing in with Keycloak and landing on the dashboard" width="960" />

The portal, the garage space, the Android app, the extranet and the supplier API all sign in through Keycloak
(OpenID Connect, authorization code with PKCE). The page is themed like the rest of the product, in French or
English, and sends each account back to where it was going.

### The fleet

#### Dashboard

<img src="assets/screenshots/dashboard.png" alt="Dashboard" />

The first screen of a fleet manager: vehicles in the fleet and assigned, overdue deadlines, contracts at risk of
a mileage overrun, open claims; the fleet by energy, by status and by Crit'Air sticker; monthly rents, average age,
average CO2 and active drivers; the savings on garage quotes; and the connected vehicles moving right now, with
their open alerts. The company selector narrows everything to one client.

#### Vehicles

<img src="assets/screenshots/vehicles.png" alt="Vehicles" />

The register of every client company: search by registration, VIN, make or model, filter by company, status,
category, energy and driver, sort every column. Vehicles are created one by one (registration and VIN validated in
the French SIV format) or imported from an Excel export, with a line-by-line error report.

<img src="assets/screenshots/vehicle.png" alt="Vehicle file" />

The vehicle file gathers everything about one vehicle: current driver, mileage history, lease contract with its
end-of-contract projection, identification, characteristics and attachment (company, site, cost centre). From
here a manager records a reading, returns the vehicle, declares a claim or changes its status. The tabs open the
deadlines, the history of drivers, the claims, the garage quotes, the telemetry, and what the driver entered from
the app:

<table>
<tr>
<td width="50%"><img src="assets/screenshots/vehicle-telemetry.png" alt="Telemetry of a vehicle" /></td>
<td width="50%"><img src="assets/screenshots/vehicle-fuel.png" alt="Fill-ups entered by the driver" /></td>
</tr>
</table>

#### Drivers

<table>
<tr>
<td width="50%"><img src="assets/screenshots/drivers.png" alt="Drivers" /></td>
<td width="50%"><img src="assets/screenshots/driver.png" alt="Driver file" /></td>
</tr>
</table>

Drivers with their employee number, licence categories, vehicle and site. A vehicle is only assigned to an active
driver whose licence covers its category: a car driver cannot be given a truck. The driver file shows the
successive vehicles, the kilometres driven with each one and the claims.

#### Contracts and deadlines

<table>
<tr>
<td width="50%"><img src="assets/screenshots/contracts.png" alt="Lease contracts" /></td>
<td width="50%"><img src="assets/screenshots/deadlines.png" alt="Deadlines" /></td>
</tr>
</table>

- **Lease contracts**: lessor, contract number, end date, contractual mileage against the mileage projected at the
  end from the real usage, and the expected gap. A contract heading for more than 5 % of overrun is flagged and the
  manager is e-mailed, so the contract can be renegotiated before the penalty.
- **Deadlines**: overdue, coming and planned, with days or kilometres left. A deadline marked as done schedules the
  next one by itself.

<table>
<tr>
<td width="50%"><img src="assets/screenshots/contract.png" alt="Contract file" /></td>
<td width="50%"><img src="assets/screenshots/vehicle-edit.png" alt="Editing a vehicle" /></td>
</tr>
</table>

The **contract file** follows a lease from start to end: rent paid so far, months elapsed, kilometres driven against
the allowance with the projected end mileage, what is left per month to stay within the contract, and the odometer of
the vehicle since the start. A contract is recorded from the vehicle file and ended from its own file. Every record
of the fleet can be **edited** (vehicle, driver, garage) with the same checks as at creation, and an edit made by
someone else in the meantime is detected instead of being overwritten.

#### Claims

<table>
<tr>
<td width="50%"><img src="assets/screenshots/incidents.png" alt="Claims" /></td>
<td width="50%"><img src="assets/screenshots/incident.png" alt="Claim declared from the driver app" /></td>
</tr>
</table>

Each claim gets a `SIN-2026-000123` reference and goes through four steps: declared, under assessment, under repair,
closed. The manager records the responsibility and the estimated cost; the photos and the GPS position come from
the driver app. The claim is declared to the insurer by the integration bus and its status follows the insurer's
answers on its own.

### Savings on garage quotes

<img src="assets/quote.gif" alt="From the quotes to negotiate to the line by line analysis" width="960" />

<table>
<tr>
<td width="50%"><img src="assets/screenshots/quotes.png" alt="Garage quotes" /></td>
<td width="50%"><img src="assets/screenshots/quote-detail.png" alt="Quote analysis" /></td>
</tr>
</table>

Quotes arrive from the garage space or as XML files. Each line is compared with the reference tariff and the
anomalies are named: _part too expensive_, _time above the standard_, _hourly rate too high_, _duplicate_,
_excessive paint ingredients_, _not in the tariff_. A conforming quote up to 300 euros is accepted on its own; the
others are accepted, rejected or negotiated with a counter-offer. Above 5,000 euros a second approval is required.
Once the work is done, the invoice is matched with the accepted quote.

<table>
<tr>
<td width="50%"><img src="assets/screenshots/savings.png" alt="Savings" /></td>
<td width="50%"><img src="assets/screenshots/tariff.png" alt="Reference tariff" /></td>
</tr>
<tr>
<td><img src="assets/screenshots/garages.png" alt="Garage network" /></td>
<td><img src="assets/screenshots/garage-detail.png" alt="Garage file" /></td>
</tr>
</table>

- **Savings**: what the comparison and the negotiation saved, month by month, what is left to negotiate, the share
  of quotes accepted without anyone, the contested invoices, the anomalies by kind and the garages to watch.
- **Reference tariff**: 38 parts by vehicle category, 30 standard times and the negotiated hourly rates of 8
  regions, with the analysis rules written in plain words.
- **Garage network**: dealerships, independent garages, tyre centres, body shops and truck workshops, with their
  hourly rate, quality score and approval; the garage file shows its overpricing rate and decision time.

#### The garage space

<table>
<tr>
<td width="50%"><img src="assets/screenshots/garage-space.png" alt="Garage space" /></td>
<td width="50%"><img src="assets/screenshots/garage-space-new.png" alt="New quote" /></td>
</tr>
<tr>
<td><img src="assets/screenshots/garage-new.png" alt="Adding a garage to the network" /></td>
<td valign="top">

The FleetPilot staff add a garage to the network (SIRET, network, hourly rate, position for the map of the drivers) and
edit it; it joins without approval, granted from its file. A garage that received a counter-offer, or wants to correct
its quote, sends a **new version**: the lines are analysed again and the history of the quote keeps every version.

</td>
</tr>
</table>

A garage signs in to its own space: counter-offers to study, quotes waiting for a decision, work to invoice, its
overpricing rate. It types a new quote line by line from the reference tariff, answers counter-offers and sends
its invoices. It only ever sees its own quotes.

### Connected vehicles

<img src="assets/live-map.gif" alt="Live map: the connected vehicles moving around Marseille, refreshed every two seconds" width="960" />

<img src="assets/screenshots/alerts.png" alt="Alerts" width="960" />

160 vehicles of the demo fleet drive on real road routes computed on OpenStreetMap and send their position,
odometer and fault codes every few seconds through Kafka. The **live map** shows them moving (Server-Sent Events),
how many are driving, idling, parked or offline, and the kilometres of the day. The **alert centre** lists what the
boxes raise: severe and minor fault codes, speeding, low battery, long idling, service due. Severe faults are
e-mailed to the team at once, and the odometer of the register follows the boxes.

### Energy transition

<img src="assets/transition.gif" alt="Energy transition page" width="960" />

<img src="assets/screenshots/transition.png" alt="Energy transition" />

For each vehicle, the platform takes the 95th percentile of its daily distances over the last 90 days, adds a
reserve, and compares it with the real range (70 % of the WLTP figure) of 15 electric models of the same size.
It then computes the full yearly cost today and in electric (rent, energy, maintenance, yearly taxes), the CO2
avoided and the low-emission zones the vehicle enters. The **greening plan** replaces each eligible vehicle at the
end of its contract, quarter by quarter until 2028, and checks the quotas of the LOM law year by year.

### Partner flows and fuel cards

<table>
<tr>
<td width="50%"><img src="assets/screenshots/flows.png" alt="Partner flows" /></td>
<td width="50%"><img src="assets/screenshots/fuel.png" alt="Fuel cards" /></td>
</tr>
</table>

The integration bus (Apache Camel) handles four flows, each with its own console: **garage quotes** in XML
(validated against an XSD, garage found by its SIRET), **lessor invoices** in CSV (matched with the contracts:
rents too high, ended or unknown contracts, duplicates), **fuel card transactions** (wrong fuel, volume above the
tank, two fill-ups in a few hours, odometer going backwards, vehicle not in the fleet) and **insurer claims**
(declared through its API, then followed every minute). Every exchange is logged line by line; a failed one can
be replayed, a file dropped twice is recognised.

### Reports and e-mails

<table>
<tr>
<td width="50%"><img src="assets/screenshots/reports.png" alt="Reports and costs" /></td>
<td width="50%"><img src="assets/screenshots/mails.png" alt="E-mail log" /></td>
</tr>
</table>

- **Reports and costs**: monthly cost per vehicle, cost per kilometre, claims per 100 vehicles, immobilisation
  rate, the total cost of the fleet over 12 months, and five reports (inventory, total cost of ownership, garage
  quotes, deadlines to handle, savings) built in the background as Excel or PDF files. The manager is e-mailed when
  the file is ready.
- **E-mail log**: every e-mail FleetPilot sent, its template, recipient and state. E-mails go through an ActiveMQ
  queue, are sent once even if a message is delivered twice, are retried, and can be sent again by hand.

### The driver app

<table>
<tr>
<td width="34%" valign="top"><img src="assets/driver-android.gif" alt="A tour of the Android driver app: home, vehicle file, fill-up, inspection, claim, garages nearby, profile" /></td>
<td width="66%" valign="top">

Drivers get a **native Android app written in Java** (MVVM, Retrofit, Room, AppAuth, WorkManager, osmdroid) and,
for iPhones, the same features in an **installable web app** (PWA) of the portal.

- **Home**: the vehicle with its number plate, the kilometres of the month or the use of the lease contract, the
  next deadline put forward, one coloured shortcut per action, the mileage to report.
- **Fill-up or charge**: litres or kWh, amount, station, odometer; the price per litre is checked on the phone.
- **Inspection**: eight points (bodywork, windscreen, tyres, lights...) with photos of every defect.
- **Claim**: type, date, GPS position, circumstances, third party, injuries, up to 10 photos, shrunk before upload.
- **Garages nearby**: approved garages around the phone, on an OpenStreetMap map.
- **Notifications**: deadlines, alerts of the box, progress of the claims.

**Offline first**: everything entered goes into an outbox on the phone (Room on Android, IndexedDB in the PWA),
with an id generated by the phone. The outbox leaves on its own when the network is back, photo by photo, and a
request sent twice is recorded once.

</td>
</tr>
</table>

<table>
<tr>
<td width="20%"><img src="assets/screenshots/android/home.png" alt="Android: home" /></td>
<td width="20%"><img src="assets/screenshots/android/fuel.png" alt="Android: fill-up" /></td>
<td width="20%"><img src="assets/screenshots/android/inspection.png" alt="Android: inspection" /></td>
<td width="20%"><img src="assets/screenshots/android/garages.png" alt="Android: garages nearby" /></td>
<td width="20%"><img src="assets/screenshots/android/offline-outbox.png" alt="Android: offline outbox" /></td>
</tr>
<tr>
<td align="center">Home</td>
<td align="center">Fill-up</td>
<td align="center">Inspection</td>
<td align="center">Garages nearby</td>
<td align="center">Offline outbox</td>
</tr>
</table>

<table>
<tr>
<td width="20%"><img src="assets/screenshots/pwa/driver-home.png" alt="PWA: home" /></td>
<td width="20%"><img src="assets/screenshots/pwa/driver-fuel.png" alt="PWA: fill-up" /></td>
<td width="20%"><img src="assets/screenshots/pwa/driver-inspection.png" alt="PWA: inspection" /></td>
<td width="20%"><img src="assets/screenshots/pwa/driver-incident.png" alt="PWA: claim" /></td>
<td width="20%"><img src="assets/screenshots/pwa/driver-notifications.png" alt="PWA: notifications" /></td>
</tr>
<tr>
<td align="center" colspan="5">The same features in the installable web app, for iPhones (tested with the WebKit engine of Safari)</td>
</tr>
</table>

<img src="assets/screenshots/pwa/driver-desktop.png" alt="PWA on a computer: the tabs in the top bar and two columns" width="960" />

On a computer the same web app spreads out: the tabs move to the top bar, the forms sit beside what was already sent,
and the map of the garages stays in view beside the list. Every driver signs in on the same page as everyone else and
lands on this space: the account decides what is shown.

### The lessor extranet (legacy application)

<img src="assets/extranet.gif" alt="A lessor filtering the contracts heading for an overrun and recording a return reading" width="960" />

<table>
<tr>
<td width="50%"><img src="assets/screenshots/extranet/contracts.png" alt="Extranet: contracts" /></td>
<td width="50%"><img src="assets/screenshots/extranet/overrun.png" alt="Extranet: overrun" /></td>
</tr>
</table>

The "v1" extranet of the lessors is a classic Java web application: servlets and JSP packaged as a WAR, deployed on
an external Tomcat 11 behind Apache httpd 2.4 (reverse proxy, security headers, cache, maintenance page). It was
modernised **without a rewrite**: its home-made passwords were
replaced by the Keycloak single sign-on, its nightly copy of the database by the fleet API. A lessor sees its
running contracts with their mileage projection, the returns within 90 days and the expected overruns, exports
them as CSV and records the reading at the return of a vehicle. It only sees its own contracts.

### Administration

<img src="assets/administration.gif" alt="Client companies, a company file, the user accounts, the settings and the activity journal" width="960" />

<table>
<tr>
<td width="50%"><img src="assets/screenshots/admin-companies.png" alt="Client companies" /></td>
<td width="50%"><img src="assets/screenshots/admin-company.png" alt="Company file with its sites and cost centers" /></td>
</tr>
<tr>
<td><img src="assets/screenshots/admin-users.png" alt="User accounts" /></td>
<td><img src="assets/screenshots/admin-user.png" alt="Account of a driver" /></td>
</tr>
<tr>
<td><img src="assets/screenshots/admin-rules.png" alt="Rules of the quote analysis" /></td>
<td><img src="assets/screenshots/admin-models.png" alt="Catalogue of electric models" /></td>
</tr>
<tr>
<td><img src="assets/screenshots/admin-zones.png" alt="Low emission zones" /></td>
<td><img src="assets/screenshots/admin-journal.png" alt="Activity journal" /></td>
</tr>
<tr>
<td><img src="assets/screenshots/admin-user-new.png" alt="New account" /></td>
<td><img src="assets/screenshots/profile.png" alt="Profile with the last actions" /></td>
</tr>
</table>

The FleetPilot staff run the platform from the portal, without touching a console:

- **Client companies**: identity, SIREN, head office and contact at the client, with the size of their fleet. Sites
  (with their position for the maps) and cost centers are added, edited and deleted, except while vehicles or drivers
  still use them. Deactivating a company keeps its history and disables the accounts of its users.
- **User accounts**: the accounts of the client managers, drivers, garages and lessors live in Keycloak and are
  managed through its admin API. The role decides what the account is attached to: a company, a driver of that
  company, a garage, a leasing company. A new account gets a temporary password, shown once, that Keycloak makes the
  user change at the first sign-in. The staff accounts come from the LDAP directory and stay read only.
- **Settings**: the tolerances of the quote analysis, the automatic approval and the approval limit of a manager
  (the tariff page shows the rules in force to the garages too); the catalogue of electric models and the low
  emission zones, after which the fleet is assessed again.
- **Activity journal**: every business action, written in the same transaction as the action, with its author, role,
  company and record: deadlines done, vehicles assigned, claims declared and assessed, contracts, accounts. A client
  manager only sees the actions on their company; everyone finds their last actions on their profile.

### Analytics and logs

<table>
<tr>
<td width="50%"><img src="assets/screenshots/kibana-fleet.png" alt="Kibana: connected fleet" /></td>
<td width="50%"><img src="assets/screenshots/kibana-logs.png" alt="Kibana: application logs" /></td>
</tr>
</table>

With the `observability` profile, a Kafka Streams pipeline turns the live flows into analytics (positions per
minute, activity per company in 5 minute windows, distance and driving time per vehicle and day, fuel consumption
from one fill-up to the next) stored in Elasticsearch, and the logs of every container are shipped by Filebeat and
Logstash. Two Kibana dashboards, generated as code, show the connected fleet and the application logs: volume per
service, errors and warnings, HTTP statuses, slowest routes, and every error with its correlation id.

## Feature reference

### Portal (fleet managers)

| Module              | Capabilities                                                                                                                  |
| ------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| Dashboard           | Fleet figures, energies, statuses, Crit'Air, costs, savings, connected vehicles, company selector                            |
| Vehicles            | Search, filters, sort, creation with SIV and VIN validation, CSV import, file with 8 tabs, status changes, readings, return |
| Drivers             | Licences, assignment rules per category, history of vehicles and kilometres, claims                                          |
| Contracts           | Lease contracts of six lessors, contract file with progress and odometer, recording and ending, overrun risk                 |
| Deadlines           | French rules, overdue / coming / planned, completion that schedules the next one, D-30 and D-7 reminders                     |
| Claims              | Declaration, assessment, repair, closing, driver photos and GPS position, insurer follow-up                                   |
| Live map and alerts | 160 vehicles live, statuses, kilometres of the day, alert centre with fault codes, speeding, battery, idling, service        |
| Garage quotes       | Line by line analysis, automatic validation, counter-offer, double approval above 5,000 euros, invoice matching               |
| Savings             | Savings by month, left to negotiate, automatic validation rate, contested invoices, anomalies, garages to watch               |
| Garage network      | 169 garages, hourly rates, quality, approval, overpricing rate per garage                                                     |
| Reference tariff    | Parts, standard times, regional hourly rates, analysis rules                                                                   |
| Reports and costs   | TCO over 12 months, cost per km, claims rate, immobilisation, Excel and PDF reports built in the background                   |
| Energy transition   | Electric eligibility from real usage, proposed model, yearly saving, CO2, low-emission zones, greening plan, LOM quotas       |
| Fuel cards          | Transactions of the providers, spending per month, anomalies                                                                   |
| Partner flows       | Console of the four integration flows, file upload, exchange detail, replay                                                    |
| E-mail log          | Every e-mail sent, state, resend                                                                                               |
| Client companies    | Identity, head office, contact, sites with their position, cost centers, activation (disables the accounts)                  |
| User accounts       | Keycloak accounts per role and attachment, temporary password, disable and enable, staff from LDAP read only                  |
| Settings            | Thresholds of the quote analysis and approval limits, electric models, low emission zones                                    |
| Activity journal    | Every business action with its author, role, company and record, filters, links to the records                              |
| Profile             | Role and scope, password and account in Keycloak, language, last actions                                                     |

### Other applications

| Application            | Capabilities                                                                                                     |
| ---------------------- | ---------------------------------------------------------------------------------------------------------------- |
| Driver app (Android)   | Home, mileage, fill-up or charge, eight-point inspection with photos, claim with GPS and photos, garages nearby, notifications, offline outbox |
| Driver app (PWA)       | The same features in the browser, installable on iPhone and Android                                              |
| Garage space           | Dashboard, new quote from the tariff, counter-offers, invoices, own quotes only                                  |
| Lessor extranet v1     | Running contracts, returns within 90 days, expected overruns, CSV export, return readings, own contracts only    |
| Kibana                 | Connected fleet dashboard, application logs dashboard                                                            |

### Platform

| Capability           | Details                                                                                                                      |
| -------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| Single sign-on       | Keycloak 26, OpenID Connect with PKCE, OpenLDAP federation for the staff, 6 roles, themed sign-in page                       |
| Tenant isolation     | Company, driver, garage and lessor taken from the token on every request: another company's vehicle is "not found"         |
| APIs                 | REST on every service with OpenAPI (Swagger UI on each service), one GraphQL schema for the portal |
| Errors               | RFC 9457 problem details with a stable code, the rule that failed for each field (translated by the portal), correlation id |
| Events               | Transactional outbox to Kafka, idempotent consumers, dead letter handling                                                    |
| Background work      | JMS queues on ActiveMQ for e-mails and reports, sent once, retried                                                           |
| Integration          | Apache Camel routes for XML, CSV and REST partners, journal, idempotence, replay                                             |
| Documents            | Purchase orders and reports as PDF, reports as Excel, 10 e-mail templates                                                    |
| Scheduled jobs       | Daily sweep (deadline reminders, lease overrun warnings), insurer follow-up, telemetry purge, energy analyses               |
| Observability        | JSON logs in Elastic Common Schema, correlation id from Nginx to the logs, health checks on every container                  |

## Tech stack

| Layer               | Technology                                                                         | Why                                                                                              |
| ------------------- | ---------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| Language            | **Java 25**                                                                        | Records, sealed types, pattern matching, virtual threads; the language of the services and of the Android app |
| Services            | **Spring Boot 4.1**, Spring Security, Spring Data JPA, **Hibernate 7**             | One coherent framework for REST, security, persistence, messaging and tests                      |
| Database            | **PostgreSQL 18**, Flyway                                                          | One database per service, versioned migrations, distance search in SQL, JSONB                   |
| Events              | **Kafka 4** (KRaft), Kafka Streams                                                 | Business events between services, telemetry flows, real-time analytics                          |
| Work queues         | **ActiveMQ Classic 6** (JMS)                                                       | E-mails and reports done once by one worker, with retries                                        |
| Integration bus     | **Apache Camel 4.22**                                                              | The engine of Red Hat JBoss Fuse and its successor, on Spring Boot |
| API for the portal  | **Spring for GraphQL**                                                             | One schema for the portal, DataLoader against N+1, user token relayed to the services            |
| Identity            | **Keycloak 26**, **OpenLDAP**                                                      | Single sign-on for every application, staff directory, roles from LDAP groups                    |
| Portal              | **Nuxt 4**, Vue 3, TypeScript, PWA, Leaflet, i18n (fr, en)                         | Typed single-page app served by Nginx, installable with offline support, hand-written design system |
| Mobile              | **Android in Java**, MVVM, Retrofit, Room, AppAuth, WorkManager, osmdroid          | A native app for the drivers, offline first                                                      |
| Legacy              | Servlets and JSP, **Tomcat 11**, **Apache httpd 2.4**                              | The existing application, kept and modernised in place                                           |
| Entry point         | **Nginx**                                                                          | One origin for the portal and every API, correlation id, Server-Sent Events                      |
| Documents           | OpenHTMLtoPDF, Apache POI, Thymeleaf                                               | PDF purchase orders and reports, Excel exports, HTML e-mails                                     |
| Analytics and logs  | **Elasticsearch, Logstash, Kibana 9**, Filebeat                                    | Centralised logs and analytics dashboards, generated as code                                     |
| Tests               | JUnit 5, Mockito, AssertJ, Testcontainers, **Cucumber** (French), **Selenium WebDriver**, **Cypress**, Playwright, Vitest, Espresso | Every level, from the domain rules to the browser and the phone       |
| Quality and CI      | Maven Wrapper, JaCoCo, Checkstyle, **SonarQube**, GitHub Actions, GitLab CI        | A quality gate on every change                                                                   |
| Containers          | **Docker**, Docker Compose with health checks and profiles                         | The whole platform with one command, Linux containers                                            |

## Architecture

```mermaid
flowchart LR
    portal[Portal Nuxt PWA] --> nginx[Nginx]
    android[Android app] --> nginx
    nginx -- /graphql --> gateway[graphql-gateway]
    nginx -- /api/v1 --> services
    gateway --> services

    subgraph services[Spring Boot services]
        vehicle[vehicle]
        quote[quote]
        telemetry[telemetry]
        energy[energy]
        notification[notification]
        reporting[reporting]
        esb[integration-esb<br/>Apache Camel]
    end

    extranet[Lessors] --> apache[Apache httpd] --> tomcat[Tomcat<br/>WAR v1] --> vehicle
    partners[Garages XML, lessors CSV,<br/>fuel cards, insurer API] <--> esb
    simulator[Vehicle simulator] --> kafka
    services <--> kafka{{Kafka}}
    notification & reporting <--> activemq{{ActiveMQ}}
    services --> postgres[(PostgreSQL<br/>one database per service)]
    kafka --> bigdata[Kafka Streams] --> elastic[(Elasticsearch)] --> kibana[Kibana]
    keycloak[Keycloak + OpenLDAP] -. tokens .-> portal & android & tomcat & services
```

| Service                | Port | Responsibility                                                                                   |
| ---------------------- | ---- | ------------------------------------------------------------------------------------------------ |
| `vehicle-service`      | 8081 | Vehicles, drivers, leases, deadlines, claims, driver app API, administration, activity journal  |
| `graphql-gateway`      | 8082 | GraphQL schema of the portal, calls the services with the user's token                           |
| `quote-service`        | 8083 | Garages, reference tariff, quote analysis and negotiation, invoices, savings                     |
| `notification-service` | 8084 | Events turned into e-mails and PDF, sent through ActiveMQ, e-mail log                            |
| `reporting-service`    | 8085 | Indicators, Excel and PDF reports built in the background                                        |
| `telemetry-service`    | 8086 | Ingestion of the boxes, trips, alert rules, live map, usage history                             |
| `vehicle-simulator`    | 8087 | 160 connected vehicles on real routes, published to Kafka                                        |
| `integration-esb`      | 8088 | Apache Camel routes: garages, lessors, fuel cards, insurer                                       |
| `bigdata-pipeline`     | 8089 | Kafka Streams analytics to Elasticsearch                                                         |
| `energy-service`       | 8090 | Electric eligibility, costs, CO2, low-emission zones, greening plan                              |
| `legacy-portal-war`    | 8095 | Lessor extranet v1, WAR on Tomcat behind Apache httpd                                            |

Key implementation choices:

- **Each service owns its data.** No service reads another one's database; it calls its API with its own
  `client_credentials` token or keeps a local copy fed by Kafka.
- **No event is lost, none is invented.** Events are written in the same transaction as the change and relayed to
  Kafka afterwards; consumers remember what they processed.
- **Kafka for facts, JMS for work.** An alert interests several services; an e-mail must be sent once.
- **The rules live in the domain.** Deadline policy, lease projection, quote analysis, telemetry rules and electric
  eligibility are plain Java, tested without Spring, in a light hexagonal layout.
- **Isolation comes from the token.** The company, driver, garage or lessor of a request is read from the Keycloak
  token, never from a parameter.
- **One correlation id end to end.** Set by Nginx, carried by the REST calls and the events, written in every log
  line and returned in every error.

### Project structure

```text
back/                   Java services, Maven multi-module (Maven Wrapper)
├── fleet-common/       Errors, correlation id, security, validators, service-to-service REST client
├── fleet-messaging/    Transactional outbox, idempotent consumers, dead letters
├── fleet-documents/    PDF and French formats shared by the services
├── vehicle-service/    ...one folder per service (see the table above)
├── legacy-portal-war/  Lessor extranet v1 (servlets, JSP, WAR)
└── acceptance-tests/   End-to-end Cucumber scenarios in French with Selenium WebDriver
front/                  Nuxt portal, garage space and driver PWA, Cypress, Playwright, Vitest
mobile/android/         Driver app, native Android in Java
infra/                  Nginx, Keycloak realm and theme, OpenLDAP, Tomcat, Apache, ELK, PostgreSQL
assets/                 Screenshots and animated GIFs of this README
tools/                  Scripts: Keycloak realm, simulator routes, Kibana dashboards, sign-in token, test data cleanup
```

## Quality & testing

Every level is tested, from the rules of the domain to a real browser and a real phone. The integration tests
start real PostgreSQL, Kafka, ActiveMQ and Elasticsearch containers (Testcontainers); the end-to-end suites run
against the whole platform started with Docker Compose.

| Suite                                | Tests          | What it covers                                                                                      |
| ------------------------------------ | -------------- | --------------------------------------------------------------------------------------------------- |
| Java services, unit and integration  | 417            | Domain rules, REST API with real tokens, isolation between companies, Kafka, JMS, Camel routes, SMTP |
| Cucumber scenarios in French         | 22 (in the 417) | Rules written for the business: assignments, deadlines and contracts, isolation, overpriced quotes |
| Selenium WebDriver + Cucumber        | 11 scenarios   | Real journeys in Chrome on the running platform: sign-in, fleet, quotes, telemetry, transition, driver, extranet, administration |
| Cypress                              | 49             | Journeys through every module, and every form sent once with a mistake (explained in French) and once valid |
| Playwright, PWA                      | 21             | The PWA on iPhone (WebKit) and Android (Chromium) profiles, offline mode, installability            |
| Playwright, site crawler             | 10             | Five accounts, every reachable page (about 150) on a desktop and on a phone: no server or JavaScript error, no error screen, broken image, untranslated text or sideways scrolling |
| Vitest                               | 17             | Portal logic: GraphQL errors, offline outbox, components, mileage curve                            |
| Legacy WAR                           | 18             | Unit tests and scenarios on an embedded Tomcat with a fake Keycloak and a fake API                  |
| Android                              | 17 + 6         | JUnit (outbox sender, rules on the phone) and Espresso on the emulator, one journey on the platform |

```bash
cd back
./mvnw verify                                   # every Java test (Docker must be running)
./mvnw -pl acceptance-tests verify -De2e        # Selenium journeys, platform started with docker compose
cd ../front
npm test                                        # Vitest
npx cypress run                                 # Cypress, platform started
npx playwright test                             # PWA on WebKit and Chromium, and the site crawler
cd ../mobile/android
./gradlew test connectedAndroidTest             # Android, emulator started
```

Code quality is enforced at build time: **Checkstyle** on every module, **JaCoCo** coverage merged from unit and
integration tests, and a **SonarQube** quality gate. The CI pipelines ([GitHub Actions](.github/workflows/ci.yml)
and [GitLab CI](.gitlab-ci.yml)) build and test the services, the portal, the WAR and the Android app, run the
SonarQube analysis and build the Docker images; on GitHub, a last job starts the whole platform with Docker Compose
and replays the Cypress and Selenium suites against it, with the Cucumber report as an artifact.

## FAQ

<details>
<summary><strong>Do I need anything else than Docker to try it?</strong></summary>

No. `docker compose up -d --build` builds every image (the Java services, the portal, the WAR), starts the
infrastructure, imports the Keycloak realm and the LDAP directory, and generates the demo data. A JDK 25 and
Node.js are only needed to run the tests outside the containers, and the Android SDK to build the app.

</details>

<details>
<summary><strong>Why several services?</strong></summary>

Fleet management mixes very different workloads: a register, a negotiation workflow, a stream of telemetry, file
exchanges with partners, background documents, analytics. Each service owns one of them, with its own database and
its own release pace, and they cooperate through their APIs and events without sharing tables.

</details>

<details>
<summary><strong>Why Apache Camel and not JBoss Fuse?</strong></summary>

Red Hat JBoss Fuse is built on Apache Camel and is being replaced by the Red Hat build of Apache Camel. The routes
written here (XML with XSD, CSV, REST, Kafka, idempotence, replay) are the same Camel routes a Fuse project uses, on
a supported runtime.

</details>

<details>
<summary><strong>Is there an iPhone app?</strong></summary>

The drivers on iPhone install the web app (PWA) of the portal: same screens, same offline outbox, notifications.
It is tested with the WebKit engine of Safari. iPhone drivers get every feature without
going through the App Store.

</details>

<details>
<summary><strong>Where do the connected vehicles come from in the demonstration?</strong></summary>

A simulator plays the telematics boxes, on real roads: 201 routes between the sites of the demonstration companies, computed with OSRM
on OpenStreetMap, with plausible behaviours (a driver in a hurry, long idling, a breakdown, an electric vehicle that
needs charging). The simulator publishes exactly what a box would, so the rest of the platform does not know the
difference.

</details>

<details>
<summary><strong>How do I reset the demonstration data?</strong></summary>

`docker compose down -v` then `docker compose up -d --build`: the databases are empty, so the demo data is
generated again. After running the end-to-end suites, `sh tools/scripts/clean-test-data.sh` removes what they
created (test claims, fill-ups, readings, quotes, and what followed them in the other services).

</details>

<details>
<summary><strong>Which ports must be free?</strong></summary>

80 (portal), 8095 (extranet), 8180 (Keycloak), 8025 (Mailpit), 8161 and 61616 (ActiveMQ), 5432 (PostgreSQL),
9094 (Kafka), 1389 (LDAP), 8081 to 8090 (services), plus 5601 and 9200 with the `observability` profile.

</details>
# FleetPilot

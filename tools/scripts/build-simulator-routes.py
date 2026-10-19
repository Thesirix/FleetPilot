"""Builds the road network of the vehicle simulator: real driving routes from each site of the demo companies to the
business places around it, computed once with the public OSRM routing service and stored in the simulator.

    python tools/scripts/build-simulator-routes.py

The simulator then needs no network access: vehicles follow real roads on the map, at the speed OSRM estimates.
Output: back/vehicle-simulator/src/main/resources/simulator/routes.json
"""
import json
import math
import sys
import time
import urllib.request

OUT = "back/vehicle-simulator/src/main/resources/simulator/routes.json"
OSRM = "https://router.project-osrm.org/route/v1/driving/{},{};{},{}?overview=full&geometries=geojson"

# Sites of the demo companies (same coordinates as the fleet demo data set)
SITES = {
    "Paris - La Défense": (48.8918, 2.2385), "Lyon - Gerland": (45.7310, 4.8281),
    "Marseille - Arenc": (43.3089, 5.3693), "Lille - Euralille": (50.6369, 3.0716),
    "Bordeaux - Lac": (44.8853, -0.5649), "Nantes - Île de Nantes": (47.2072, -1.5507),
    "Toulouse - Basso Cambo": (43.5706, 1.3929), "Strasbourg - Wacken": (48.5981, 7.7623),
    "Marseille - Hôtel de Métropole": (43.2924, 5.3592), "Aix-en-Provence - Centre technique": (43.4925, 5.3611),
    "Aubagne - Dépôt des Paluds": (43.2917, 5.6012), "La Ciotat - Dépôt municipal": (43.1915, 5.6181),
    "Vitrolles - Centre d'exploitation": (43.4483, 5.2451), "Martigues - Dépôt de Croix-Sainte": (43.4140, 5.0285),
    "Vitrolles - Plateforme logistique": (43.4312, 5.2541), "Fos-sur-Mer - Terminal": (43.4377, 4.8566),
    "Avignon - Agence": (43.9218, 4.8605),
}

# Business places the vehicles drive to: customers, industrial zones, ports, airports
PLACES = {
    "Marseille - La Valentine": (43.3089, 5.4842), "Marseille - Prado": (43.2610, 5.3760),
    "Marseille - Castellane": (43.2856, 5.3842), "Marseille - Saint-Antoine": (43.3726, 5.3639),
    "Marseille - Château-Gombert": (43.3637, 5.4436), "Marseille - Saint-Loup": (43.2779, 5.4325),
    "Marseille - Euroméditerranée": (43.3125, 5.3697), "Aix-en-Provence - Centre": (43.5263, 5.4454),
    "Aix-en-Provence - Les Milles": (43.5040, 5.3884), "Marignane - Aéroport": (43.4367, 5.2150),
    "Gémenos - Zone d'activités": (43.2950, 5.6290), "Plan-de-Campagne": (43.4105, 5.3680),
    "Rousset - Zone industrielle": (43.4847, 5.6253), "Istres - Centre": (43.5137, 4.9874),
    "Salon-de-Provence": (43.6403, 5.0973), "Toulon - Port": (43.1180, 5.9300),
    "Cavaillon - Marché d'intérêt national": (43.8372, 5.0390), "Avignon - Courtine": (43.9190, 4.8240),
    "Arles": (43.6770, 4.6310), "Nîmes - Ville active": (43.8090, 4.3910),
    "Nanterre": (48.8925, 2.2069), "Saint-Denis": (48.9245, 2.3601), "Rungis": (48.7496, 2.3480),
    "Paris - Vaugirard": (48.8417, 2.3000), "Gennevilliers - Port": (48.9320, 2.2860),
    "Boulogne-Billancourt": (48.8350, 2.2410), "Versailles": (48.8049, 2.1204),
    "Lyon - Part-Dieu": (45.7605, 4.8597), "Vaulx-en-Velin": (45.7790, 4.9230), "Saint-Priest": (45.7020, 4.9380),
    "Corbas": (45.6680, 4.9020), "Villeurbanne": (45.7665, 4.8795),
    "Roubaix": (50.6910, 3.1810), "Lesquin": (50.5890, 3.1110), "Villeneuve-d'Ascq": (50.6230, 3.1450),
    "Mérignac": (44.8400, -0.6450), "Bègles": (44.8070, -0.5480), "Bassens": (44.9020, -0.5160),
    "Saint-Herblain": (47.2170, -1.6480), "Carquefou": (47.2970, -1.4910), "Rezé": (47.1900, -1.5650),
    "Blagnac": (43.6370, 1.3900), "Labège": (43.5340, 1.5130), "Toulouse - Capitole": (43.6045, 1.4440),
    "Strasbourg - Port du Rhin": (48.5730, 7.7960), "Illkirch": (48.5290, 7.7150), "Schiltigheim": (48.6070, 7.7500),
}

MAX_KM = 75  # as the crow flies: beyond, a site does not serve the place


def distance_km(a, b):
    lat1, lon1, lat2, lon2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2
    return 6371 * 2 * math.asin(math.sqrt(h))


def simplify(points, tolerance=0.00008):
    """Douglas-Peucker: keeps the shape of the road with far fewer points."""
    if len(points) < 3:
        return points
    start, end = points[0], points[-1]
    dx, dy = end[0] - start[0], end[1] - start[1]
    norm = math.hypot(dx, dy) or 1e-12
    worst, index = 0.0, 0
    for i in range(1, len(points) - 1):
        d = abs(dy * (points[i][0] - start[0]) - dx * (points[i][1] - start[1])) / norm
        if d > worst:
            worst, index = d, i
    if worst <= tolerance:
        return [start, end]
    return simplify(points[: index + 1], tolerance)[:-1] + simplify(points[index:], tolerance)


def route(a, b):
    url = OSRM.format(a[1], a[0], b[1], b[0])
    request = urllib.request.Request(url, headers={"User-Agent": "FleetPilot demo data builder"})
    with urllib.request.urlopen(request, timeout=30) as response:
        data = json.load(response)
    best = data["routes"][0]
    points = [(round(lat, 5), round(lon, 5)) for lon, lat in best["geometry"]["coordinates"]]
    return round(best["distance"]), round(best["duration"]), simplify(points)


def main():
    routes = []
    pairs = [(s, p) for s, sc in SITES.items() for p, pc in PLACES.items() if distance_km(sc, pc) <= MAX_KM]
    print(f"{len(pairs)} routes to compute", file=sys.stderr)
    for i, (site, place) in enumerate(pairs, 1):
        for attempt in range(3):
            try:
                metres, seconds, points = route(SITES[site], PLACES[place])
                routes.append({"from": site, "to": place, "distance": metres, "duration": seconds,
                               "points": [[lat, lon] for lat, lon in points]})
                break
            except Exception as error:  # noqa: BLE001 - public service, retried
                print(f"retry {site} -> {place}: {error}", file=sys.stderr)
                time.sleep(3)
        if i % 20 == 0:
            print(f"{i}/{len(pairs)}", file=sys.stderr)
        time.sleep(1.1)  # fair use of the public OSRM demo server
    document = {
        "source": "OpenStreetMap contributors, routes computed with OSRM",
        "sites": [{"name": n, "lat": c[0], "lon": c[1]} for n, c in SITES.items()],
        "places": [{"name": n, "lat": c[0], "lon": c[1]} for n, c in PLACES.items()],
        "routes": routes,
    }
    with open(OUT, "w", encoding="utf-8") as out:
        json.dump(document, out, ensure_ascii=False, separators=(",", ":"))
    total = sum(len(r["points"]) for r in routes)
    print(f"written {OUT}: {len(routes)} routes, {total} points", file=sys.stderr)


if __name__ == "__main__":
    main()

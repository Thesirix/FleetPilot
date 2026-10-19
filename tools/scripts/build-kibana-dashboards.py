"""Builds the Kibana saved objects of FleetPilot (data views, Lens charts, saved search, dashboards).

    python tools/scripts/build-kibana-dashboards.py

Writes infra/elk/kibana/dashboards/fleetpilot.ndjson, imported by the kibana-setup container. Writing the objects as
code keeps them reviewable and reproducible, instead of a raw export edited by hand.
"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'infra', 'elk', 'kibana', 'dashboards', 'fleetpilot.ndjson')
LAYER = 'layer1'
objects = []


# ------------------------------------------------------------------------------------------------- data views

def painless_switch(field, labels):
    """A runtime field giving the French label of a code, so that the charts never show raw enum values."""
    cases = ' '.join("if (v == '%s') { emit('%s'); return; }" % (code, label.replace("'", "\'"))
                     for code, label in labels.items())
    return {'type': 'keyword', 'script': {'source': "if (doc['%s'].size() == 0) { return; } "
                                                   "String v = doc['%s'].value; %s emit(v);" % (field, field, cases)}}


CATEGORY = {'CAR': 'Voiture', 'VAN': 'Utilitaire', 'TRUCK': 'Poids lourd', 'MACHINE': 'Engin de chantier'}
ENERGY = {'DIESEL': 'Diesel', 'PETROL': 'Essence', 'ELECTRIC': 'Électrique', 'HYBRID': 'Hybride',
          'PLUG_IN_HYBRID': 'Hybride rechargeable', 'LPG': 'GPL', 'CNG': 'GNV', 'HYDROGEN': 'Hydrogène'}
ALERT_TYPE = {'SPEEDING': 'Excès de vitesse', 'LONG_IDLE': 'Ralenti prolongé', 'LOW_BATTERY': 'Batterie faible',
              'ENGINE_FAULT': 'Défaut grave', 'ENGINE_WARNING': 'Défaut à surveiller', 'SERVICE_DUE': 'Révision proche'}
SEVERITY = {'HIGH': 'Grave', 'MEDIUM': 'Moyen', 'LOW': 'Mineur', 'CRITICAL': 'Critique', 'WARNING': 'Avertissement',
            'INFO': 'Information'}
VEHICLE_LABELS = {'categoryLabel': painless_switch('category', CATEGORY), 'energyLabel': painless_switch('energy', ENERGY)}


def data_view(view_id, pattern, name, time_field, runtime=None):
    attributes = {'title': pattern, 'name': name, 'timeFieldName': time_field}
    if runtime:
        attributes['runtimeFieldMap'] = json.dumps(runtime, ensure_ascii=False)
    objects.append({'type': 'index-pattern', 'id': view_id, 'references': [], 'attributes': attributes})


data_view('fleetpilot-vehicle-day', 'fleet-vehicle-day', 'Usage quotidien des véhicules', 'day', VEHICLE_LABELS)
data_view('fleetpilot-activity', 'fleet-activity', 'Activité de la flotte (5 min)', 'windowStart')
data_view('fleetpilot-positions', 'fleet-positions', 'Positions des véhicules', 'at', VEHICLE_LABELS)
data_view('fleetpilot-alerts', 'fleet-alerts', 'Alertes des véhicules connectés', 'raisedAt',
          {'categoryLabel': painless_switch('category', CATEGORY), 'typeLabel': painless_switch('type', ALERT_TYPE),
           'severityLabel': painless_switch('severity', SEVERITY)})
data_view('fleetpilot-faults', 'fleet-faults', 'Codes défaut', 'at',
          {**VEHICLE_LABELS, 'severityLabel': painless_switch('severity', SEVERITY)})
data_view('fleetpilot-fuel', 'fleet-fuel', 'Transactions carburant', 'at', VEHICLE_LABELS)
data_view('fleetpilot-logs', 'fleetpilot-logs-*', 'Journaux applicatifs', '@timestamp')


# ------------------------------------------------------------------------------------------------- Lens columns

def date_histogram(field, interval='auto'):
    return {'label': field, 'dataType': 'date', 'operationType': 'date_histogram', 'sourceField': field,
            'isBucketed': True, 'scale': 'interval',
            'params': {'interval': interval, 'includeEmptyRows': True, 'dropPartials': False}}


def terms(field, label, order_by, size=10, other=False):
    return {'label': label, 'customLabel': True, 'dataType': 'string', 'operationType': 'terms', 'sourceField': field,
            'isBucketed': True, 'scale': 'ordinal',
            'params': {'size': size, 'orderBy': {'type': 'column', 'columnId': order_by}, 'orderDirection': 'desc',
                       'otherBucket': other, 'missingBucket': False, 'parentFormat': {'id': 'terms'},
                       'include': [], 'exclude': [], 'includeIsRegex': False, 'excludeIsRegex': False}}


def metric(operation, field, label, decimals=0, kql=None):
    column = {'label': label, 'customLabel': True, 'dataType': 'number', 'operationType': operation,
              'sourceField': field if field else '___records___', 'isBucketed': False, 'scale': 'ratio',
              'params': {'emptyAsNull': True, 'format': {'id': 'number', 'params': {'decimals': decimals}}}}
    if kql:
        column['filter'] = {'query': kql, 'language': 'kuery'}
    return column


def count(label, kql=None):
    column = metric('count', None, label, 0, kql)
    column['params']['emptyAsNull'] = False  # no error is 0, not "N/A"
    return column


# ------------------------------------------------------------------------------------------------- Lens charts

def lens(lens_id, title, view_id, columns, order, visualization_type, visualization, kql=''):
    objects.append({
        'type': 'lens', 'id': lens_id,
        'attributes': {
            'title': title, 'description': '', 'visualizationType': visualization_type,
            'state': {
                'datasourceStates': {'formBased': {'layers': {LAYER: {
                    'columns': columns, 'columnOrder': order, 'incompleteColumns': {}, 'sampling': 1}}}},
                'visualization': visualization,
                'query': {'query': kql, 'language': 'kuery'}, 'filters': [],
                'internalReferences': [], 'adHocDataViews': {},
            },
        },
        'references': [{'type': 'index-pattern', 'id': view_id, 'name': 'indexpattern-datasource-layer-' + LAYER}],
    })
    return lens_id


def metric_chart(lens_id, title, view_id, column, kql=''):
    return lens(lens_id, title, view_id, {'m': column}, ['m'], 'lnsMetric',
                {'layerId': LAYER, 'layerType': 'data', 'metricAccessor': 'm'}, kql)


def xy_chart(lens_id, title, view_id, x, y, split=None, series='bar_stacked', kql='', legend='right'):
    columns = {'x': x, 'y': y}
    order = ['x', 'y']
    layer = {'layerId': LAYER, 'layerType': 'data', 'seriesType': series, 'xAccessor': 'x', 'accessors': ['y'],
             'position': 'top', 'showGridlines': False}
    if split:
        columns['s'] = split
        order = ['s', 'x', 'y']
        layer['splitAccessor'] = 's'
    return lens(lens_id, title, view_id, columns, order, 'lnsXY', {
        'legend': {'isVisible': split is not None, 'position': legend},
        'valueLabels': 'hide', 'fittingFunction': 'Linear' if series == 'line' else 'None',
        'preferredSeriesType': series, 'layers': [layer],
        'axisTitlesVisibilitySettings': {'x': False, 'yLeft': False, 'yRight': False},
        'gridlinesVisibilitySettings': {'x': False, 'yLeft': True, 'yRight': True},
    }, kql)


def table(lens_id, title, view_id, columns, kql=''):
    ids = list(columns.keys())
    return lens(lens_id, title, view_id, columns, ids, 'lnsDatatable', {
        'layerId': LAYER, 'layerType': 'data', 'columns': [{'columnId': c} for c in ids],
    }, kql)


# ------------------------------------------------------------------------------------------------- saved search

def saved_search(search_id, title, view_id, columns, kql):
    source = {'query': {'query': kql, 'language': 'kuery'}, 'filter': [],
              'indexRefName': 'kibanaSavedObjectMeta.searchSourceJSON.index'}
    objects.append({
        'type': 'search', 'id': search_id,
        'attributes': {'title': title, 'description': '', 'columns': columns, 'sort': [['@timestamp', 'desc']],
                       'kibanaSavedObjectMeta': {'searchSourceJSON': json.dumps(source)}},
        'references': [{'name': 'kibanaSavedObjectMeta.searchSourceJSON.index', 'type': 'index-pattern',
                        'id': view_id}],
    })
    return search_id


# ------------------------------------------------------------------------------------------------- dashboards

def dashboard(dashboard_id, title, description, panels, time_from, refresh_ms):
    panels_json, references = [], []
    for index, panel in enumerate(panels, start=1):
        kind, ref_id, x, y, w, h = panel[:6]
        time_range = panel[6] if len(panel) > 6 else None
        name = 'panel_%d' % index
        config = {'enhancements': {}}
        if h <= 7:
            config['hidePanelTitles'] = True
        if time_range:
            # a panel with its own period (the live activity only makes sense over the last hours)
            config['timeRange'] = {'from': time_range, 'to': 'now'}
        panels_json.append({'type': kind, 'gridData': {'x': x, 'y': y, 'w': w, 'h': h, 'i': str(index)},
                            'panelIndex': str(index), 'embeddableConfig': config, 'panelRefName': name})
        references.append({'name': name, 'type': kind, 'id': ref_id})
    objects.append({
        'type': 'dashboard', 'id': dashboard_id,
        'attributes': {
            'title': title, 'description': description, 'panelsJSON': json.dumps(panels_json),
            'optionsJSON': json.dumps({'useMargins': True, 'syncColors': True, 'syncCursor': True,
                                       'syncTooltips': False, 'hidePanelTitles': False}),
            'timeRestore': True, 'timeFrom': time_from, 'timeTo': 'now',
            'refreshInterval': {'pause': False, 'value': refresh_ms},
            'kibanaSavedObjectMeta': {'searchSourceJSON': json.dumps(
                {'query': {'query': '', 'language': 'kuery'}, 'filter': []})},
        },
        'references': references,
    })


# Fleet: what the connected vehicles did
fleet = [
    ('lens', metric_chart('fleet-km', 'Kilomètres parcourus', 'fleetpilot-vehicle-day',
                          metric('sum', 'distanceKm', 'Kilomètres parcourus')), 0, 0, 12, 7),
    ('lens', metric_chart('fleet-vehicles', 'Véhicules utilisés', 'fleetpilot-vehicle-day',
                          metric('unique_count', 'vehicleId', 'Véhicules utilisés'), 'distanceKm > 0'), 12, 0, 12, 7),
    ('lens', metric_chart('fleet-alerts-count', 'Alertes levées', 'fleetpilot-alerts',
                          count('Alertes levées')), 24, 0, 12, 7),
    ('lens', metric_chart('fleet-consumption', 'Consommation moyenne (L/100 km)', 'fleetpilot-fuel',
                          metric('average', 'consumptionL100', 'Consommation moyenne (L/100 km)', 1)), 36, 0, 12, 7),
    ('lens', xy_chart('fleet-km-per-day', 'Kilomètres par jour et par catégorie', 'fleetpilot-vehicle-day',
                      date_histogram('day', '1d'), metric('sum', 'distanceKm', 'Kilomètres'),
                      terms('categoryLabel', 'Catégorie', 'y', 5)), 0, 7, 32, 15),
    ('lens', xy_chart('fleet-alerts-by-type', 'Alertes par type', 'fleetpilot-alerts',
                      terms('typeLabel', 'Type', 'y', 8), count('Alertes'), series='bar_horizontal'), 32, 7, 16, 15),
    ('lens', xy_chart('fleet-moving', 'Véhicules en mouvement sur 6 heures, par entreprise', 'fleetpilot-activity',
                      date_histogram('windowStart'), metric('max', 'movingVehicles', 'Véhicules en mouvement'),
                      terms('companyName', 'Entreprise', 'y', 5), series='line'), 0, 22, 32, 15, 'now-6h'),
    ('lens', xy_chart('fleet-consumption-by-category', 'Consommation moyenne par catégorie (L/100 km)',
                      'fleetpilot-fuel', terms('categoryLabel', 'Catégorie', 'y', 5),
                      metric('average', 'consumptionL100', 'L/100 km', 1), series='bar_horizontal'), 32, 22, 16, 15),
    ('lens', table('fleet-usage-by-company', 'Usage par entreprise', 'fleetpilot-vehicle-day', {
        'c': terms('companyName', 'Entreprise', 'k', 10),
        'k': metric('sum', 'distanceKm', 'Kilomètres'),
        'v': metric('unique_count', 'vehicleId', 'Véhicules'),
        'd': metric('average', 'drivingMinutes', 'Conduite moyenne par jour (min)'),
        's': metric('max', 'maxSpeed', 'Vitesse maximale (km/h)'),
    }), 0, 37, 28, 14),
    ('lens', table('fleet-fault-codes', 'Codes défaut les plus fréquents', 'fleetpilot-faults', {
        'c': terms('code', 'Code', 'n', 10),
        'g': terms('severityLabel', 'Gravité', 'n', 3),
        'n': count('Occurrences'),
        'v': metric('unique_count', 'vehicleId', 'Véhicules'),
    }), 28, 37, 20, 14),
]
dashboard('fleetpilot-fleet', 'FleetPilot : flotte connectée',
          'Usage des véhicules connectés, activité en direct, alertes, codes défaut et consommation. '
          'Alimenté par le pipeline Kafka Streams.', fleet, 'now-30d', 60000)

# Logs: what the platform did
saved_search('logs-latest-errors', 'Dernières erreurs', 'fleetpilot-logs',
             ['service.name', 'log.level', 'message', 'trace.correlation_id'], 'log.level : "ERROR"')
logs = [
    ('lens', metric_chart('logs-events', 'Événements', 'fleetpilot-logs', count('Événements')), 0, 0, 12, 7),
    ('lens', metric_chart('logs-errors', 'Erreurs', 'fleetpilot-logs',
                          count('Erreurs', 'log.level : "ERROR"')), 12, 0, 12, 7),
    ('lens', metric_chart('logs-requests', 'Requêtes HTTP', 'fleetpilot-logs',
                          count('Requêtes HTTP', 'event.dataset : "nginx.access"')), 24, 0, 12, 7),
    ('lens', metric_chart('logs-response-time', 'Temps de réponse moyen (s)', 'fleetpilot-logs',
                          metric('average', 'event.duration_seconds', 'Temps de réponse (s)', 3,
                                 'event.dataset : "nginx.access"')), 36, 0, 12, 7),
    ('lens', xy_chart('logs-volume', 'Volume de journaux par service', 'fleetpilot-logs',
                      date_histogram('@timestamp'), count('Événements'),
                      terms('service.name', 'Service', 'y', 10, other=True)), 0, 7, 30, 15),
    ('lens', xy_chart('logs-problems', 'Erreurs et avertissements par service', 'fleetpilot-logs',
                      terms('service.name', 'Service', 'y', 10), count('Événements'),
                      terms('log.level', 'Niveau', 'y', 2), series='bar_horizontal_stacked',
                      kql='log.level : ("ERROR" or "WARN")'), 30, 7, 18, 15),
    ('lens', xy_chart('logs-http-status', 'Réponses HTTP par statut', 'fleetpilot-logs',
                      date_histogram('@timestamp'), count('Réponses'),
                      terms('http.response.status_code', 'Statut', 'y', 6), kql='event.dataset : "nginx.access"'),
     0, 22, 30, 14),
    ('lens', table('logs-slowest', 'Routes les plus lentes', 'fleetpilot-logs', {
        'p': terms('url.route', 'Route', 't', 10),
        't': metric('average', 'event.duration_seconds', 'Durée moyenne (s)', 3),
        'n': count('Requêtes'),
    }, 'event.dataset : "nginx.access"'), 30, 22, 18, 14),
    ('search', 'logs-latest-errors', 0, 36, 48, 16),
]
dashboard('fleetpilot-logs', 'FleetPilot : journaux applicatifs',
          'Journaux de tous les conteneurs, collectés par Filebeat et analysés par Logstash : volume, erreurs, '
          'requêtes HTTP. Un identifiant de corrélation relie le portail, la passerelle et les services.',
          logs, 'now-1h', 30000)

# The objects are written in the current formats (Lens "formBased" datasource...): their migration version says so,
# so that Kibana applies only the migrations of later versions, not those written for the formats of Kibana 7.
MIGRATION = {'index-pattern': '8.0.0', 'search': '8.0.0', 'lens': '8.9.0', 'dashboard': '8.9.0'}
for obj in objects:
    obj['coreMigrationVersion'] = '8.8.0'
    obj['typeMigrationVersion'] = MIGRATION[obj['type']]

with open(OUT, 'w', encoding='utf-8', newline='\n') as out:
    for obj in objects:
        out.write(json.dumps(obj, ensure_ascii=False) + '\n')
print('%d saved objects written to %s' % (len(objects), os.path.normpath(OUT)))

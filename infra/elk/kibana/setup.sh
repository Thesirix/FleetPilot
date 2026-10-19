#!/bin/bash
# Prepares Kibana once the stack is up: index template of the logs, data views and dashboards, settings.
# Run by the one shot "kibana-setup" container; running it again is harmless (objects are overwritten).
set -euo pipefail
ES=http://elasticsearch:9200
KB=http://kibana:5601

echo "Index template of the logs"
curl -sS -f -X PUT "$ES/_index_template/fleetpilot-logs" -H 'Content-Type: application/json' \
  --data-binary @/setup/logs-template.json
echo

echo "Data views and dashboards"
curl -sS -f -X POST "$KB/api/saved_objects/_import?overwrite=true" -H 'kbn-xsrf: true' \
  --form file=@/setup/dashboards/fleetpilot.ndjson
echo

echo "Settings: Paris time, French dates, default data view"
curl -sS -f -X POST "$KB/api/kibana/settings" -H 'kbn-xsrf: true' -H 'x-elastic-internal-origin: kibana' -H 'Content-Type: application/json' \
  -d '{"changes":{"dateFormat:tz":"Europe/Paris","dateFormat":"D MMM YYYY, HH:mm","dateFormat:dow":"Monday","defaultIndex":"fleetpilot-vehicle-day","format:number:defaultPattern":"0,0.[00]","format:number:defaultLocale":"fr","format:currency:defaultPattern":"0,0.[00] $","theme:darkMode":false}}'
echo
echo "Kibana ready: http://localhost:5601/app/dashboards"

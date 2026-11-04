#!/usr/bin/env bash
# Gera carga de CPU no order-service para observar o HPA.
# Uso: scripts/load.sh [URL] [PARALELO] [SEGUNDOS]
URL=${1:-http://localhost:8080}; N=${2:-8}; SECS=${3:-120}
end=$((SECONDS+SECS))
for _ in $(seq "$N"); do
  ( while [ $SECONDS -lt $end ]; do curl -s -o /dev/null "$URL/load/cpu?millis=300"; done ) &
done
wait

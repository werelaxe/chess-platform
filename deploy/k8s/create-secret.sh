#!/usr/bin/env bash
# Creates the Secret "chess" with a random database password, JWT key, admin token and Grafana
# password, unless it exists. The database password only takes effect when the database is
# initialised (first start on an empty volume); changing it later needs ALTER USER in the
# database as well. To add the admin token to an existing secret:
#   kubectl -n chess patch secret chess -p "{\"stringData\":{\"admin-token\":\"$(openssl rand -hex 24)\"}}"
#   kubectl -n chess rollout restart deployment/api
# and the Grafana password (the "admin" login of docs/ARCHITECTURE.md section 4.1; Grafana
# cannot start without the key and reads it at every start, as it keeps no database):
#   kubectl -n chess patch secret chess -p "{\"stringData\":{\"grafana-password\":\"$(openssl rand -hex 24)\"}}"
#   kubectl -n chess get secret chess -o jsonpath='{.data.grafana-password}' | base64 --decode
set -euo pipefail

kubectl get namespace chess >/dev/null 2>&1 || kubectl create namespace chess
if kubectl -n chess get secret chess >/dev/null 2>&1; then
    echo "secret chess/chess already exists"
    exit 0
fi
kubectl -n chess create secret generic chess \
    --from-literal=postgres-password="$(openssl rand -hex 24)" \
    --from-literal=jwt-secret="$(openssl rand -base64 48)" \
    --from-literal=admin-token="$(openssl rand -hex 24)" \
    --from-literal=grafana-password="$(openssl rand -hex 24)"

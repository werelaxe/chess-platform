#!/usr/bin/env bash
# Rolls the app out to the cluster of the current kubectl context:
#   deploy/k8s/deploy.sh <commit SHA>   the images the Deploy workflow published for that commit
#   deploy/k8s/deploy.sh                manifest changes only, keeping the version that runs now
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
tag=${1:-$(kubectl -n chess get deployment api \
    -o jsonpath='{.spec.template.spec.containers[0].image}' 2>/dev/null | sed -n 's/.*://p')}
if [ -z "$tag" ]; then
    echo "usage: $0 TAG (nothing runs yet to take the version from)" >&2
    exit 2
fi

# The manifests carry no tag, so that applying them can never roll the app back by accident;
# a copy of them gets the release's.
release=$(mktemp -d)
trap 'rm -rf "$release"' EXIT
cp "$here"/*.yaml "$release/"
cat >>"$release/kustomization.yaml" <<EOF
images:
  - {name: ghcr.io/werelaxe/chess-platform-api, newTag: "$tag"}
  - {name: ghcr.io/werelaxe/chess-platform-engine, newTag: "$tag"}
  - {name: ghcr.io/werelaxe/chess-platform-web, newTag: "$tag"}
EOF

echo "deploying $tag"
kubectl apply -k "$release"
for workload in statefulset/db deployment/engine deployment/api deployment/web \
    deployment/kube-state-metrics deployment/prometheus deployment/grafana; do
    kubectl -n chess rollout status "$workload" --timeout=5m
done

#!/usr/bin/env bash
# Gives the Deploy workflow its access to the cluster (deployer.yaml) and prints a kubeconfig
# for it. Run it with an administrator's kubectl context, after create-secret.sh, and store the
# output as the repository secret:
#   deploy/k8s/ci/kubeconfig.sh | gh secret set KUBE_CONFIG --repo werelaxe/chess-platform
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
kubectl apply -f "$here/deployer.yaml" >&2

# The token controller fills the Secret in shortly after it is created.
token=
for _ in $(seq 1 30); do
    token=$(kubectl -n chess get secret deployer-token -o jsonpath='{.data.token}')
    [ -n "$token" ] && break
    sleep 1
done
if [ -z "$token" ]; then
    echo "the token of the deployer service account did not appear" >&2
    exit 1
fi
server=$(kubectl config view --minify -o jsonpath='{.clusters[0].cluster.server}')
ca=$(kubectl config view --minify --raw -o jsonpath='{.clusters[0].cluster.certificate-authority-data}')

cat <<EOF
apiVersion: v1
kind: Config
clusters:
  - name: cluster
    cluster:
      server: $server
      certificate-authority-data: $ca
users:
  - name: deployer
    user:
      token: $(printf '%s' "$token" | base64 --decode)
contexts:
  - name: deployer
    context:
      cluster: cluster
      user: deployer
      namespace: chess
current-context: deployer
EOF

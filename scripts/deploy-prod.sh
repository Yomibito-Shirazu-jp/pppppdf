#!/usr/bin/env bash
#
# Deploy the current main HEAD to the internal Stirling-PDF production VM.
#
# Layout: there is no Cloud Build trigger for this repo. Production lives on a
# single GCE VM (`stirling-pdf` in `aidriven-mastering-fyqu`, asia-northeast1-b)
# at http://34.84.0.231/. Each deploy is a manual two-step:
#
#   1. `gcloud builds submit` — Cloud Build packages the worktree and produces
#      a Docker image tagged `:latest` in Artifact Registry.
#   2. SSH to the VM, pull the new image, and `docker run` it with the same
#      env/volume/port set the previous container used.
#
# Run from a clean checkout of `main`. The script refuses to deploy if the
# working tree has uncommitted changes — production should always reflect a
# pushed commit, never a developer's untracked WIP.
#
# Tunables (override via environment):
#   PROJECT     GCP project (default: aidriven-mastering-fyqu)
#   REGISTRY    Artifact Registry path of the image
#   ZONE        VM zone (default: asia-northeast1-b)
#   VM_NAME     VM instance name (default: stirling-pdf)
#   SKIP_BUILD  =1 to skip the Cloud Build step and only redeploy :latest
#   SKIP_GIT_CHECK  =1 to bypass the clean-tree guard (use with care)

set -euo pipefail

PROJECT="${PROJECT:-aidriven-mastering-fyqu}"
REGISTRY="${REGISTRY:-asia-northeast1-docker.pkg.dev/${PROJECT}/cloud-run-source-deploy/stirling-pdf}"
IMAGE="${REGISTRY}:latest"
ZONE="${ZONE:-asia-northeast1-b}"
VM_NAME="${VM_NAME:-stirling-pdf}"

# ── Pre-flight: clean tree on main (override with SKIP_GIT_CHECK=1) ─────────
if [[ "${SKIP_GIT_CHECK:-0}" != "1" ]]; then
    BRANCH="$(git rev-parse --abbrev-ref HEAD)"
    if [[ "$BRANCH" != "main" ]]; then
        echo "ERROR: current branch is '$BRANCH', expected 'main'." >&2
        echo "       Run from a main checkout or set SKIP_GIT_CHECK=1." >&2
        exit 1
    fi
    if [[ -n "$(git status --porcelain)" ]]; then
        echo "ERROR: working tree has uncommitted changes." >&2
        echo "       Commit / stash first, or set SKIP_GIT_CHECK=1." >&2
        exit 1
    fi
fi

# ── Step 1: build & push image ─────────────────────────────────────────────
if [[ "${SKIP_BUILD:-0}" != "1" ]]; then
    echo "[1/2] Cloud Build → ${IMAGE}"
    gcloud builds submit . \
        --tag="${IMAGE}" \
        --project="${PROJECT}" \
        --timeout=60m \
        --machine-type=e2-highcpu-32
else
    echo "[1/2] Skipping build (SKIP_BUILD=1) — will redeploy existing :latest"
fi

# ── Step 2: pull + recreate container on the VM ────────────────────────────
echo "[2/2] Recreating container on ${VM_NAME} (${ZONE})"

# Env / volumes / ports mirror the original container on the VM. Update here
# (and only here) if the deployment shape changes.
gcloud compute ssh "${VM_NAME}" \
    --zone="${ZONE}" \
    --project="${PROJECT}" \
    --command="
set -e
IMG='${IMAGE}'
echo '─── pulling new image'
sudo docker pull \"\$IMG\"
echo '─── stopping previous container'
sudo docker stop stirling-pdf || true
sudo docker rm stirling-pdf || true
echo '─── starting new container'
sudo docker run -d \\
    --name stirling-pdf \\
    --restart=always \\
    -p 80:8080 \\
    -p 8080:8080 \\
    -v /opt/stirling-pdf/customFiles:/customFiles \\
    -v /opt/stirling-pdf/logs:/logs \\
    -v /opt/stirling-pdf/pipeline:/pipeline \\
    -v /opt/stirling-pdf/configs:/configs \\
    -e DOCKER_ENABLE_SECURITY=true \\
    -e SECURITY_ENABLELOGIN=true \\
    -e SECURITY_INITIALLOGIN_USERNAME=admin \\
    -e SECURITY_INITIALLOGIN_PASSWORD=changeme \\
    -e DISABLE_ADDITIONAL_FEATURES=false \\
    -e SYSTEM_DEFAULTLOCALE=ja-JP \\
    -e SYSTEM_GOOGLEVISIBILITY=false \\
    -e SHOW_SURVEY=false \\
    -e SYSTEM_MAXFILESIZE=500 \\
    \"\$IMG\"
echo '─── prune dangling images'
sudo docker image prune -f >/dev/null
"

# ── Health check ───────────────────────────────────────────────────────────
echo "Waiting for Spring Boot to come up…"
for i in {1..20}; do
    sleep 6
    code="$(curl -sk -o /dev/null -w '%{http_code}' http://34.84.0.231/api/v1/proprietary/ui-data/login || true)"
    if [[ "$code" == "200" ]]; then
        echo "✓ live (HTTP 200)"
        echo "  build: $(curl -sIk http://34.84.0.231/favicon.ico | grep -i last-modified)"
        exit 0
    fi
    echo "  [$i/20] HTTP $code — still warming up"
done
echo "WARN: container running but health endpoint hasn't returned 200 within 2 minutes." >&2
echo "      Check logs: gcloud compute ssh ${VM_NAME} --zone=${ZONE} --project=${PROJECT} --command='sudo docker logs --tail 200 stirling-pdf'" >&2
exit 2

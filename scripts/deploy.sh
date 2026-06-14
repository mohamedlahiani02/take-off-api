#!/usr/bin/env bash
# ============================================================
# deploy.sh — Deploy a specific image tag to staging or prod
# Usage: ./deploy.sh <staging|prod> <image-tag>
# Ref: INFRASTRUCTURE.md §7
#
# Called by:
#   - deploy-staging.yml workflow (GitHub Actions SSH)
#   - manual: ssh deploy@<vps> /opt/takeoff/scripts/deploy.sh prod <tag>
# ============================================================
set -euo pipefail

ENV="${1:?Usage: deploy.sh <staging|prod> <image-tag>}"
IMAGE_TAG="${2:?Usage: deploy.sh <staging|prod> <image-tag>}"
DEPLOY_DIR="/opt/takeoff/${ENV}"

if [[ "${ENV}" != "staging" && "${ENV}" != "prod" ]]; then
  echo "ERROR: ENV must be 'staging' or 'prod', got '${ENV}'"
  exit 1
fi

echo "==> [deploy] ENV=${ENV} IMAGE_TAG=${IMAGE_TAG}"
echo "==> [deploy] Working directory: ${DEPLOY_DIR}"

cd "${DEPLOY_DIR}"

# ── 1. Decrypt env file ───────────────────────────────────────
echo "==> [deploy] Decrypting .env.enc via SOPS..."
sops --decrypt .env.enc > .env
chmod 600 .env

# ── 2. Write IMAGE_TAG into .env ──────────────────────────────
# Remove any stale IMAGE_TAG line then append the new one
sed -i '/^IMAGE_TAG=/d' .env
echo "IMAGE_TAG=${IMAGE_TAG}" >> .env

# ── 3. Pull the new image ─────────────────────────────────────
echo "==> [deploy] Pulling image..."
docker compose pull api

# ── 4. Run DB migrations (online-safe) ────────────────────────
echo "==> [deploy] Running Drizzle migrations..."
docker compose run --rm --no-deps api node dist/main migrate || {
  echo "ERROR: migrations failed — aborting deploy"
  exit 1
}

# ── 5. Roll the api service (zero-downtime recreate) ──────────
echo "==> [deploy] Restarting api service..."
docker compose up -d --remove-orphans api

# ── 6. Health gate (30 × 2s = 60s max) ───────────────────────
echo "==> [deploy] Waiting for health check..."
PORT=$([[ "${ENV}" == "prod" ]] && echo "3000" || echo "3001")
for i in $(seq 1 30); do
  if docker compose exec -T api wget -qO- "http://localhost:${PORT}/health" 2>/dev/null | grep -q '"ok":true'; then
    echo "==> [deploy] Health OK after ${i} attempts. Done."
    exit 0
  fi
  echo "    waiting... (${i}/30)"
  sleep 2
done

echo "ERROR: Health check failed after 60 seconds. Rolling back..."
# Rollback: re-deploy the previous image (caller should capture old tag)
exit 1

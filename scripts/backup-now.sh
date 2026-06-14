#!/usr/bin/env bash
# ============================================================
# backup-now.sh — Trigger an ad-hoc pgBackRest backup
# Usage: ./backup-now.sh <staging|prod>
#
# Runs a full backup via the pgbackrest sidecar container.
# Ref: INFRASTRUCTURE.md §9.2 (pgBackRest)
# ============================================================
set -euo pipefail

ENV="${1:?Usage: backup-now.sh <staging|prod>}"
DEPLOY_DIR="/opt/takeoff/${ENV}"

if [[ "${ENV}" != "staging" && "${ENV}" != "prod" ]]; then
  echo "ERROR: ENV must be 'staging' or 'prod'"
  exit 1
fi

echo "==> [backup-now] Starting ad-hoc pgBackRest backup for ${ENV}"
cd "${DEPLOY_DIR}"

# Ensure stanza exists (idempotent)
echo "==> [backup-now] Initialising stanza (if not already done)..."
docker compose exec pgbackrest pgbackrest --stanza="takeoff-${ENV}" stanza-create || true

# Run a full backup
echo "==> [backup-now] Running full backup..."
docker compose exec pgbackrest pgbackrest \
  --stanza="takeoff-${ENV}" \
  --type=full \
  backup

echo "==> [backup-now] Backup complete. Listing latest backups:"
docker compose exec pgbackrest pgbackrest \
  --stanza="takeoff-${ENV}" \
  info

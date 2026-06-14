#!/usr/bin/env bash
# ============================================================
# restore.sh — Interactive restore from R2 backup into scratch DB
# Usage: ./restore.sh <staging|prod> [YYYY-MM-DD]
#
# IMPORTANT: This restores into a SCRATCH database named takeoff_restore.
# It does NOT touch the live database.
# To replace live: stop services, swap, restart — manual step (intentional).
# Ref: INFRASTRUCTURE.md §9 / BOOTSTRAP.md Step 12
# ============================================================
set -euo pipefail

ENV="${1:?Usage: restore.sh <staging|prod> [YYYY-MM-DD]}"
RESTORE_DATE="${2:-}"
DEPLOY_DIR="/opt/takeoff/${ENV}"
SCRATCH_DB="takeoff_restore_$(date +%Y%m%d%H%M%S)"

echo "==> [restore] ENV=${ENV}  RESTORE_DATE=${RESTORE_DATE:-latest}"
echo "==> [restore] Scratch DB: ${SCRATCH_DB}"
echo ""
read -r -p "WARNING: This will restore data from R2 into ${SCRATCH_DB}. Continue? [y/N] " confirm
if [[ "${confirm,,}" != "y" ]]; then
  echo "Aborted."
  exit 0
fi

cd "${DEPLOY_DIR}"

# ── Create scratch DB ─────────────────────────────────────────
echo "==> [restore] Creating scratch database ${SCRATCH_DB}..."
docker compose exec -T postgres createdb -U takeoff "${SCRATCH_DB}"

# ── Restore via pgBackRest ────────────────────────────────────
RESTORE_OPTS="--stanza=takeoff-${ENV} --db-path=/var/lib/postgresql/data_restore"
if [[ -n "${RESTORE_DATE}" ]]; then
  RESTORE_OPTS="${RESTORE_OPTS} --target=${RESTORE_DATE}T23:59:59+00"
fi

echo "==> [restore] Restoring from backup..."
docker compose exec pgbackrest pgbackrest restore ${RESTORE_OPTS}

# ── Basic sanity check ────────────────────────────────────────
echo "==> [restore] Running sanity checks..."
USER_COUNT=$(docker compose exec -T postgres psql -U takeoff -d "${SCRATCH_DB}" -tAc \
  "SELECT COUNT(*) FROM identity.users" 2>/dev/null || echo "0")
echo "==> [restore] Users in restored DB: ${USER_COUNT}"
if [[ "${USER_COUNT}" -gt 0 ]]; then
  echo "==> [restore] Sanity check PASSED."
else
  echo "WARNING: User count is 0 — verify the restore manually."
fi

echo ""
echo "==> [restore] Restore complete. Scratch DB: ${SCRATCH_DB}"
echo "    Connect: docker compose exec postgres psql -U takeoff -d ${SCRATCH_DB}"
echo "    Drop when done: docker compose exec postgres dropdb -U takeoff ${SCRATCH_DB}"

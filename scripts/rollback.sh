#!/usr/bin/env bash
# ============================================================
# rollback.sh — Roll back to a previous image tag
# Usage: ./rollback.sh <staging|prod> <previous-image-tag>
#
# Image tags are immutable (sha-pinned) so rollback is just
# re-running deploy with the old tag.
# Ref: INFRASTRUCTURE.md §7 (rollback note)
# ============================================================
set -euo pipefail

ENV="${1:?Usage: rollback.sh <staging|prod> <previous-image-tag>}"
PREV_TAG="${2:?Usage: rollback.sh <staging|prod> <previous-image-tag>}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==> [rollback] Rolling back ${ENV} to ${PREV_TAG}"
exec "${SCRIPT_DIR}/deploy.sh" "${ENV}" "${PREV_TAG}"

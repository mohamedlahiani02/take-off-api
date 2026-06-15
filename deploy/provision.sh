#!/usr/bin/env bash
# Take Off API — OVH (Debian/Ubuntu) VPS bootstrap.
# Run as root on a fresh VPS:  bash provision.sh <deploy-user>
# Idempotent-ish: safe to re-run.
set -euo pipefail

DEPLOY_USER="${1:-takeoff}"

echo "==> Updating system"
export DEBIAN_FRONTEND=noninteractive
apt-get update -y && apt-get upgrade -y

echo "==> Installing base packages"
apt-get install -y ca-certificates curl git ufw fail2ban unattended-upgrades

echo "==> Installing Docker Engine + compose plugin"
if ! command -v docker >/dev/null 2>&1; then
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc 2>/dev/null \
    || curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  . /etc/os-release
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
https://download.docker.com/linux/${ID} ${VERSION_CODENAME} stable" > /etc/apt/sources.list.d/docker.list
  apt-get update -y
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
systemctl enable --now docker

echo "==> Creating deploy user '${DEPLOY_USER}'"
if ! id "${DEPLOY_USER}" >/dev/null 2>&1; then
  adduser --disabled-password --gecos "" "${DEPLOY_USER}"
fi
usermod -aG docker "${DEPLOY_USER}"

echo "==> Firewall (allow SSH + HTTP + HTTPS only)"
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

echo "==> Enabling unattended security upgrades"
dpkg-reconfigure -f noninteractive unattended-upgrades || true

cat <<EOF

==> Provisioning complete.
Next steps (as ${DEPLOY_USER}):
  git clone <repo-url> ~/take-off-api && cd ~/take-off-api
  cp deploy/.env.example deploy/.env   # fill in real secrets
  mkdir -p keys && cp /path/to/private.pem keys/ && cp /path/to/public.pem keys/
  docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build

Point the API_DOMAIN A-record at this server's IP BEFORE the first boot so Caddy
can obtain a TLS certificate. Verify: curl https://<API_DOMAIN>/actuator/health
EOF

#!/usr/bin/env bash
# ============================================================
# provision-vps.sh — Idempotent VPS provisioning script
# Run once as root on a fresh Ubuntu 24.04 LTS VPS.
# Safe to re-run — every step checks before acting.
# Ref: INFRASTRUCTURE.md §11 (Security hardening Day-1 checklist)
#      BOOTSTRAP.md Step 7
#
# Usage:
#   DOMAIN=takeoff.tn bash provision-vps.sh
# ============================================================
set -euo pipefail

DOMAIN="${DOMAIN:-takeoff.tn}"
DEPLOY_USER="deploy"
SOPS_VERSION="3.8.1"
AGE_VERSION="1.1.1"
CADDY_VERSION="2.8.4"

log() { echo "==> [provision] $*"; }
ok()  { echo "    OK: $*"; }

# ── 0. Root check ─────────────────────────────────────────────
if [[ "${EUID}" -ne 0 ]]; then
  echo "ERROR: Must run as root."
  exit 1
fi

# ── 1. OS check ───────────────────────────────────────────────
log "Checking OS..."
if ! grep -q 'Ubuntu 24.04' /etc/os-release 2>/dev/null; then
  echo "WARNING: Expected Ubuntu 24.04. Continuing anyway."
fi

# ── 2. Base packages ──────────────────────────────────────────
log "Updating package lists and installing base packages..."
apt-get update -qq
apt-get upgrade -y -qq
apt-get install -y -qq \
  curl ca-certificates gnupg lsb-release \
  unattended-upgrades apt-listchanges \
  fail2ban ufw wget git jq

# ── 3. Unattended upgrades (security patches only) ────────────
log "Configuring unattended-upgrades..."
cat > /etc/apt/apt.conf.d/50unattended-upgrades << 'EOF'
Unattended-Upgrade::Allowed-Origins {
  "${distro_id}:${distro_codename}-security";
};
Unattended-Upgrade::AutoFixInterruptedDpkg "true";
Unattended-Upgrade::MinimalSteps "true";
Unattended-Upgrade::Remove-Unused-Dependencies "true";
Unattended-Upgrade::Automatic-Reboot "false";
EOF
systemctl enable --now unattended-upgrades
ok "unattended-upgrades configured"

# ── 4. Docker CE ──────────────────────────────────────────────
if ! command -v docker &>/dev/null; then
  log "Installing Docker CE..."
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
    gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  chmod a+r /etc/apt/keyrings/docker.gpg
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
    https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -qq
  apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-compose-plugin
  ok "Docker CE installed"
else
  ok "Docker already installed: $(docker --version)"
fi

# ── 5. Caddy 2 ────────────────────────────────────────────────
if ! command -v caddy &>/dev/null; then
  log "Installing Caddy 2..."
  apt-get install -y -qq debian-keyring debian-archive-keyring apt-transport-https
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | \
    gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  echo "deb [signed-by=/usr/share/keyrings/caddy-stable-archive-keyring.gpg] \
    https://dl.cloudsmith.io/public/caddy/stable/deb/debian any-version main" \
    > /etc/apt/sources.list.d/caddy-stable.list
  apt-get update -qq
  apt-get install -y -qq caddy
  ok "Caddy installed: $(caddy version)"
else
  ok "Caddy already installed: $(caddy version)"
fi

# ── 6. age + SOPS ─────────────────────────────────────────────
if ! command -v age &>/dev/null; then
  log "Installing age ${AGE_VERSION}..."
  ARCH=$(dpkg --print-architecture)
  AGE_ASSET="age-v${AGE_VERSION}-linux-${ARCH}.tar.gz"
  wget -q "https://github.com/FiloSottile/age/releases/download/v${AGE_VERSION}/${AGE_ASSET}" \
    -O /tmp/age.tar.gz
  tar -xzf /tmp/age.tar.gz -C /tmp
  install -m 0755 /tmp/age/age /usr/local/bin/age
  install -m 0755 /tmp/age/age-keygen /usr/local/bin/age-keygen
  rm -rf /tmp/age /tmp/age.tar.gz
  ok "age installed: $(age --version)"
else
  ok "age already installed: $(age --version)"
fi

if ! command -v sops &>/dev/null; then
  log "Installing SOPS ${SOPS_VERSION}..."
  ARCH=$(dpkg --print-architecture)
  wget -q "https://github.com/getsops/sops/releases/download/v${SOPS_VERSION}/sops-v${SOPS_VERSION}.linux.${ARCH}" \
    -O /usr/local/bin/sops
  chmod 0755 /usr/local/bin/sops
  ok "SOPS installed: $(sops --version)"
else
  ok "SOPS already installed: $(sops --version)"
fi

# ── 7. UFW firewall ───────────────────────────────────────────
log "Configuring UFW..."
ufw --force reset
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp    comment 'SSH'
ufw allow 80/tcp    comment 'HTTP (Caddy ACME challenge)'
ufw allow 443/tcp   comment 'HTTPS (Caddy)'
ufw --force enable
ok "UFW enabled"
ufw status verbose

# ── 8. SSH hardening ──────────────────────────────────────────
log "Hardening sshd_config..."
SSHD_CONFIG="/etc/ssh/sshd_config"
cp "${SSHD_CONFIG}" "${SSHD_CONFIG}.bak.$(date +%s)"

# Set keys-only root login and disable password auth
sed -i 's/^#*PermitRootLogin.*/PermitRootLogin prohibit-password/' "${SSHD_CONFIG}"
sed -i 's/^#*PasswordAuthentication.*/PasswordAuthentication no/' "${SSHD_CONFIG}"
sed -i 's/^#*ChallengeResponseAuthentication.*/ChallengeResponseAuthentication no/' "${SSHD_CONFIG}"

# Validate before reloading
sshd -t && systemctl reload sshd
ok "sshd hardened (key-only, root login by key only)"

# ── 9. fail2ban ───────────────────────────────────────────────
log "Configuring fail2ban..."
cat > /etc/fail2ban/jail.local << 'EOF'
[DEFAULT]
bantime  = 1h
findtime = 10m
maxretry = 5

[sshd]
enabled = true
port    = ssh
logpath = %(sshd_log)s
backend = %(sshd_backend)s
EOF
systemctl enable --now fail2ban
ok "fail2ban configured"

# ── 10. Deploy user ───────────────────────────────────────────
log "Creating deploy user..."
if ! id "${DEPLOY_USER}" &>/dev/null; then
  useradd -m -s /bin/bash "${DEPLOY_USER}"
  usermod -aG docker "${DEPLOY_USER}"
  ok "Created user: ${DEPLOY_USER}"
else
  ok "User already exists: ${DEPLOY_USER}"
  # Ensure docker group membership
  usermod -aG docker "${DEPLOY_USER}"
fi

# Copy root's authorized_keys to deploy user (so the same SSH key works)
DEPLOY_SSH_DIR="/home/${DEPLOY_USER}/.ssh"
mkdir -p "${DEPLOY_SSH_DIR}"
if [[ -f /root/.ssh/authorized_keys ]]; then
  cp /root/.ssh/authorized_keys "${DEPLOY_SSH_DIR}/authorized_keys"
  ok "Copied authorized_keys to ${DEPLOY_USER}"
fi
chown -R "${DEPLOY_USER}:${DEPLOY_USER}" "${DEPLOY_SSH_DIR}"
chmod 700 "${DEPLOY_SSH_DIR}"
chmod 600 "${DEPLOY_SSH_DIR}/authorized_keys" 2>/dev/null || true

# Sudo: deploy user can ONLY run the deploy script without password
SUDOERS_FILE="/etc/sudoers.d/deploy"
if [[ ! -f "${SUDOERS_FILE}" ]]; then
  echo "${DEPLOY_USER} ALL=(ALL) NOPASSWD: /opt/takeoff/scripts/deploy.sh" > "${SUDOERS_FILE}"
  chmod 440 "${SUDOERS_FILE}"
  ok "sudoers entry created for ${DEPLOY_USER}"
else
  ok "sudoers entry already exists"
fi

# ── 11. Directory layout ──────────────────────────────────────
log "Creating /opt/takeoff directory layout..."
for dir in staging prod scripts; do
  if [[ ! -d "/opt/takeoff/${dir}" ]]; then
    mkdir -p "/opt/takeoff/${dir}"
    ok "Created /opt/takeoff/${dir}"
  else
    ok "/opt/takeoff/${dir} already exists"
  fi
done
for dir in staging prod; do
  mkdir -p "/opt/takeoff/${dir}/media"
  mkdir -p "/opt/takeoff/${dir}/secrets"
done
chown -R "${DEPLOY_USER}:${DEPLOY_USER}" /opt/takeoff
ok "Directory ownership set to ${DEPLOY_USER}"

# ── 12. Caddyfile ─────────────────────────────────────────────
log "Installing system Caddyfile..."
CADDYFILE_SRC="/opt/takeoff/scripts/Caddyfile"
CADDYFILE_DST="/etc/caddy/Caddyfile"

if [[ -f "${CADDYFILE_SRC}" ]]; then
  cp "${CADDYFILE_SRC}" "${CADDYFILE_DST}"
  ok "Caddyfile installed from ${CADDYFILE_SRC}"
else
  log "No Caddyfile found at ${CADDYFILE_SRC} — generating minimal placeholder..."
  cat > "${CADDYFILE_DST}" << EOF
{
  email ops@${DOMAIN}
}

api.${DOMAIN} {
  reverse_proxy 127.0.0.1:3000
}

staging.api.${DOMAIN} {
  basicauth /* {
    deploy REPLACE_WITH_BCRYPT_HASH
  }
  reverse_proxy 127.0.0.1:3001
}

media.${DOMAIN} {
  root * /opt/takeoff/prod/media
  file_server
}
EOF
  ok "Placeholder Caddyfile written — replace REPLACE_WITH_BCRYPT_HASH"
fi

# Set DOMAIN env var for Caddy systemd
if [[ ! -f /etc/caddy/caddy.env ]]; then
  echo "DOMAIN=${DOMAIN}" > /etc/caddy/caddy.env
fi

# ── 13. SOPS age key placeholder ──────────────────────────────
log "Setting up SOPS age key directory..."
AGE_KEY_DIR="/root/.config/sops/age"
mkdir -p "${AGE_KEY_DIR}"
if [[ ! -f "${AGE_KEY_DIR}/keys.txt" ]]; then
  cat > "${AGE_KEY_DIR}/keys.txt" << 'EOF'
# PLACEHOLDER — replace with your real age private key(s)
# Format: one key per line
# Generate: age-keygen -o /tmp/key.txt && cat /tmp/key.txt
# AGE-SECRET-KEY-1XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
EOF
  chmod 600 "${AGE_KEY_DIR}/keys.txt"
  ok "SOPS age key placeholder created at ${AGE_KEY_DIR}/keys.txt"
else
  ok "SOPS age keys.txt already exists"
fi

# ── 14. Enable services ───────────────────────────────────────
log "Enabling and starting services..."
systemctl enable --now docker
systemctl enable --now caddy
ok "docker and caddy enabled"

# ── 15. Final report ──────────────────────────────────────────
echo ""
echo "=================================================="
echo "  PROVISIONING COMPLETE"
echo "=================================================="
echo ""
echo "  Domain          : ${DOMAIN}"
echo "  Public IP       : $(curl -fsSL https://ifconfig.me 2>/dev/null || echo 'unknown')"
echo "  Deploy user     : ${DEPLOY_USER}"
if [[ -f "${DEPLOY_SSH_DIR}/authorized_keys" ]]; then
  echo "  Auth keys SHA   : $(sha256sum "${DEPLOY_SSH_DIR}/authorized_keys" | cut -d' ' -f1)"
fi
echo "  Docker          : $(docker --version)"
echo "  Caddy           : $(caddy version)"
echo "  SOPS            : $(sops --version)"
echo "  age             : $(age --version)"
echo ""
echo "  UFW status:"
ufw status numbered | head -20
echo ""
echo "NEXT STEPS:"
echo "  1. Replace the age key placeholder in ${AGE_KEY_DIR}/keys.txt"
echo "  2. Update the staging htpasswd hash in /etc/caddy/Caddyfile"
echo "  3. Copy compose files to /opt/takeoff/staging/ and /opt/takeoff/prod/"
echo "  4. Add SOPS-encrypted .env.enc to each compose directory"
echo "  5. Reload Caddy: systemctl reload caddy"
echo ""

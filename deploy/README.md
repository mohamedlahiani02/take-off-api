# Take Off API — OVH deployment

Run the existing Docker image on a self-managed VPS, behind Caddy (auto-TLS), with a
private Postgres. No application code changes — this only packages the app for a host.

## Files
- `docker-compose.prod.yml` — db + api + caddy. Postgres is internal-only; Caddy exposes 80/443.
- `Caddyfile` — reverse proxy + automatic HTTPS for `$API_DOMAIN`.
- `.env.example` — copy to `.env` (git-ignored) and fill in real secrets.
- `provision.sh` — one-shot VPS bootstrap (Docker, deploy user, UFW, fail2ban, auto-upgrades).

## First deploy
1. **DNS first:** point an A-record (e.g. `api.takeoff.tn`) at the VPS IP. Caddy needs this
   resolvable to issue the Let's Encrypt cert.
2. **Provision:** as root — `bash deploy/provision.sh takeoff`
3. **Secrets:** as the deploy user —
   ```bash
   cp deploy/.env.example deploy/.env   # edit: strong POSTGRES_PASSWORD, FRONTEND_URL, API_DOMAIN
   mkdir -p keys                         # copy private.pem + public.pem here (PKCS#8 / X.509)
   ```
4. **Launch:**
   ```bash
   docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build
   ```
5. **Verify:** `curl https://$API_DOMAIN/actuator/health` → `{"status":"UP"}` over TLS.

## Data migration (Railway → OVH)
Pre-launch with only test data: skip — Flyway creates the schema and `V2` seeds products.

To carry real data:
```bash
# dump from Railway (use the Railway connection string)
pg_dump --no-owner --no-acl "postgresql://USER:PASS@HOST:PORT/DB" -Fc -f takeoff.dump
# restore into the OVH db container
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env exec -T db \
  pg_restore --no-owner --no-acl -U "$POSTGRES_USER" -d "$POSTGRES_DB" < takeoff.dump
```

## Cutover & rollback
- Point the frontend at the new origin: set Vercel `NEXT_PUBLIC_API_URL` (and the prototype
  `TAKEOFF_API_URL` injection in `take-off-web/lib/prototype/serve.ts`) to `https://$API_DOMAIN`.
- Verify end-to-end, then shut down Railway.
- **Rollback:** revert `NEXT_PUBLIC_API_URL` to the Railway URL — instant, no redeploy of the API.

## Security notes
- The DB password used on Railway is compromised (shared in chat) — use a **new** strong password here.
- `deploy/.env` and `keys/*.pem` are git-ignored; never commit them.
- Postgres has no published host port; it's only reachable on the compose-internal network.

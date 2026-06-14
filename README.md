# Take Off API

NestJS 11 + Fastify backend for the Take Off sports club platform (Padel + Pilates + Store).

## References

- [ARCHITECTURE.md](../ARCHITECTURE.md) — domain model, API contracts, business logic
- [INFRASTRUCTURE.md](../INFRASTRUCTURE.md) — Docker Compose, Caddy, CI/CD, secrets
- [BOOTSTRAP.md](../BOOTSTRAP.md) — Day-1 setup guide (start here if you're new)

## Local development quickstart

### Prerequisites

- Node.js 20+
- pnpm 9+
- Docker + Docker Compose plugin

### 1. Install dependencies

```bash
pnpm install
```

### 2. Generate JWT keys

```bash
openssl genrsa -out jwt.private.pem 4096
openssl rsa -in jwt.private.pem -pubout -out jwt.public.pem
```

### 3. Start backing services

```bash
docker compose -f infra/local/docker-compose.yml up -d
```

This starts Postgres 16, Redis 7, Mailhog (SMTP), and MinIO (S3).

### 4. Configure environment

```bash
cp .env.example .env
# Edit .env — DATABASE_URL, REDIS_URL are pre-filled for local Docker
```

### 5. Run migrations

```bash
pnpm db:migrate
```

### 6. Start the API

```bash
pnpm dev
```

API is live at `http://localhost:3001`.
OpenAPI docs at `http://localhost:3001/docs`.

## Available scripts

| Script | Description |
|---|---|
| `pnpm dev` | Start with watch mode (ts-node via nest CLI) |
| `pnpm build` | Compile TypeScript to `dist/` |
| `pnpm start` | Run compiled `dist/main.js` |
| `pnpm lint` | ESLint with zero-warning policy |
| `pnpm typecheck` | TypeScript compiler check (no emit) |
| `pnpm test` | Vitest unit tests |
| `pnpm db:generate` | Generate a new Drizzle migration from schema changes |
| `pnpm db:migrate` | Apply pending migrations |
| `pnpm db:studio` | Open Drizzle Studio (DB browser) |
| `pnpm db:check` | Check for schema drift |

## Project structure

```
src/
├── main.ts                  # Fastify bootstrap, Swagger, Sentry
├── app.module.ts            # Root module
├── common/
│   ├── problem-details.filter.ts   # RFC 7807 exception filter
│   ├── idempotency.interceptor.ts  # Idempotency-Key Redis caching
│   ├── audit.interceptor.ts        # Audit log placeholder
│   └── current-user.decorator.ts
├── infrastructure/
│   ├── drizzle.module.ts / drizzle.service.ts
│   ├── redis.module.ts / redis.service.ts
│   ├── r2.client.ts
│   ├── resend.client.ts
│   └── konnect.client.ts
└── modules/
    ├── identity/     # Auth, registration, JWT
    ├── padel/        # Courts, slots, bookings, packs, ranking, tournaments
    ├── pilates/      # Class definitions, sessions, reservations
    ├── catalog/      # Products, variants
    ├── orders/       # Checkout, orders
    ├── payments/     # Payment intents, webhooks
    ├── wallet/       # Ledger, top-up
    ├── ranking/      # ELO, ladder
    ├── coaching/     # Coaches, inquiries
    ├── content/      # Partners, FAQ, hours
    └── notifications/ # Outbox consumer worker
```

## Environment variables

See [.env.example](.env.example) for the full list with descriptions.

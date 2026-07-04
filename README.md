# Take Off API

**Spring Boot 3.5 + Kotlin** REST backend for the Take Off sports club platform in Sfax, Tunisia
(Padel · Pilates · Store · Coaching). Serves the [`take-off-web`](https://github.com/mohamedlahiani02/take-off-web)
frontend over a versioned `/api/v1` HTTP API.

## Tech stack

- **Kotlin 1.9.25** on **JVM 21**, **Spring Boot 3.5** (Web MVC, Data JPA, Security, Validation, Actuator)
- **PostgreSQL** with **Flyway** migrations (schema is versioned, never auto-DDL'd)
- **RS256 JWT** auth (Auth0 `java-jwt`) — separate member and admin security chains
- **Cloudinary** for image upload / CDN
- **SpringDoc / Swagger UI** for API docs
- **Konnect** payment gateway (server-initiated intents + signed webhook)
- Gradle (Kotlin DSL) build, containerised via `Dockerfile`

## Architecture

See **[ARCHITECTURE.md](./ARCHITECTURE.md)** for the full system design (frontend + backend),
the feature-first package layout, the Controller → Service → Gateway → Repository layering, the
security model, and the payment flow.

## Local development

### Prerequisites

- **JDK 21**
- A **PostgreSQL** instance (local or Docker)

### 1. Generate JWT keys (RS256)

```bash
scripts/gen-keys.sh        # writes keys/private.pem and keys/public.pem
```

### 2. Configure environment

```bash
cp .env.example .env       # set DATABASE_URL, JWT_*_KEY_PATH, FRONTEND_URL, …
```

Secrets (DB credentials, JWT keys) are supplied via environment variables only — **never commit
them**. In production, Railway injects `DATABASE_URL`.

### 3. Run

```bash
./gradlew bootRun          # API on http://localhost:8080
```

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`

Flyway applies pending migrations from `src/main/resources/db/migration` on startup.

## Gradle tasks

| Task | Description |
|---|---|
| `./gradlew bootRun` | Run the API locally |
| `./gradlew build` | Compile + test + assemble the jar |
| `./gradlew test` | JUnit 5 test suite |
| `./gradlew bootJar` | Build the runnable jar (used by the Docker image) |

## Project structure

```
src/main/kotlin/tn/takeoff/
├── auth          # JWT issue/verify, login, refresh, password reset
├── users         # member profile / identity
├── wallet        # TND credit ledger
├── products      # catalog + variants
├── orders        # cart checkout → orders, status lifecycle
├── courts        # padel courts, availability, blocks, bookings
├── classes       # pilates schedule + packs
├── coaches       # public coach profiles
├── coaching      # coaching-inquiry funnel (lead capture)
├── packs         # credit packs
├── payments      # Konnect payment intents + webhook fulfillment
├── tournaments   # ladder / events
├── cms           # editable site content
├── common        # errors, pagination
├── config        # security, JWT props, web/CORS, env diagnostics
└── admin         # staff-facing management endpoints (per domain)

src/main/resources/db/migration/   # Flyway V1__… … V20__…
```

## Environment variables

See [.env.example](.env.example) for the full list with descriptions.

# Take Off — System Architecture

> **You are reading the copy in `take-off-api` (backend).** The same document ships in the
> [`take-off-web`](https://github.com/mohamedlahiani02/take-off-web) frontend repo so either
> repository tells the whole story. Backend-specific sections are marked **⟨BE⟩**, frontend
> ones **⟨FE⟩**.

**Take Off** is the digital platform for a sports club in **Sfax, Tunisia** running two businesses
under one roof — a **padel** venue (2 courts) and a **pilates** studio (reformer + mat) — plus a
unified **online store** and a **coaching** funnel. The product goal is to let visitors browse
freely (courts, classes, products, coaches) without an account, and gate only *transactions*
(bookings, orders, packs) behind a single identity with a shared wallet.

The system is a classic **decoupled frontend + REST API backend**, deployed as two independently
shipped services.

---

## 1. High-level topology

```
                          ┌──────────────────────────────────────────┐
        Browser  ───────► │  take-off-web  (Vercel)                  │
                          │  Next.js 16 App Router · React 19 · TS   │
                          │                                          │
                          │  • Public pages served as static         │
                          │    "declarative component" HTML through  │
                          │    Next.js route handlers                │
                          │  • Client runtime + admin panel (vanilla │
                          │    JS modules: auth / cart / api-client) │
                          └───────────────┬──────────────────────────┘
                                          │  HTTPS  (JSON, Bearer JWT)
                                          │  window.TAKEOFF_API_URL
                                          ▼
                          ┌──────────────────────────────────────────┐
                          │  take-off-api  (Render)                  │
                          │  Spring Boot 3.5 · Kotlin 1.9 · JVM 21   │
                          │                                          │
                          │  Controller → Service → Gateway →        │
                          │              Repository (JPA)            │
                          │  • RS256 JWT auth (member + admin chains)│
                          │  • Flyway-migrated schema                │
                          └───────┬───────────────────────┬──────────┘
                                  │                        │
                          ┌───────▼────────┐      ┌────────▼─────────┐
                          │ PostgreSQL     │      │ Cloudinary       │
                          │ (Render)       │      │ (image upload/CDN)│
                          └────────────────┘      └──────────────────┘

    Payments: Konnect (Tunisian gateway) via server-initiated payment intents + webhook.
```

Two deploy pipelines, no shared runtime. The only contract between them is the **REST API**
(`/api/v1/**`) and the JWT format.

---

## 2. Repositories

| Repo | Responsibility | Stack |
|---|---|---|
| [`take-off-web`](https://github.com/mohamedlahiani02/take-off-web) | Public site, member account UI, admin panel | Next.js 16, React 19, TypeScript, Tailwind v4 |
| [`take-off-api`](https://github.com/mohamedlahiani02/take-off-api) | REST API, auth, domain logic, persistence | Spring Boot 3.5, Kotlin 1.9, JPA/Hibernate, PostgreSQL, Flyway |

---

## 3. ⟨FE⟩ Frontend architecture

### 3.1 The unusual bit: static "declarative component" pages served through Next.js

The public marketing/commerce pages (`padel`, `pilates`, `store`, `coaches`, gateway) are authored
as **`prototype/*.dc.html`** — self-contained HTML documents driven by a tiny client-side runtime
(`support.js`) that understands `{{ binding }}`, `sc-if`, and `sc-for` directives. They are **not**
React components.

They reach production through thin Next.js **route handlers** that read the file, rewrite its
relative asset references, and stream it:

- `lib/prototype/serve.ts` — `prototypeHtml()` serves a page with `cache-control: no-store`;
  `rewritePrototypeHtml()` maps every `./asset` reference (JS, CSS, images, video, logo) onto a
  cache-busted `/prototype-assets/...` route. **This rewrite map is an explicit allowlist** — a new
  shared asset must be added here or it 404s in production while still working on a local static
  server.
- `app/prototype-assets/[...path]/route.ts` → `prototypeAsset()` resolves and serves the physical
  file from `prototype/` with case-insensitive path matching and long-lived caching (JS gets a
  1-day TTL so script changes propagate).

**Why this shape?** The pages started life as a rapidly-iterated design prototype. Rather than
rewrite pixel-perfect, animation-heavy marketing pages into React, they are shipped as-is and
wrapped by Next.js for hosting, routing, headers/CSP, and API co-location. The trade-off is
explicit and documented (§8).

### 3.2 Client-side runtime (vanilla JS modules under `prototype/`)

| Module | Role |
|---|---|
| `support.js` | The `.dc.html` rendering runtime (interpolation, conditionals, loops). |
| `api-client.js` | Fetch wrapper. Base URL from `window.TAKEOFF_API_URL` (injected server-side by `serve.ts`); attaches Bearer JWT; transparent **access/refresh token** rotation via `/api/v1/auth/refresh`; degrades to a read-only "offline" mode when no API URL is configured. |
| `auth.js` | Account drawer, login/register/profile, order & booking history. Output is HTML-escaped via `esc()` to prevent stored XSS. |
| `cart.js` | Cart + multi-step checkout (delivery/pickup, payment method, order placement, Konnect redirect prep). |
| `menu.js` | Shared nav + footer (embedded Google Map to the Sfax location). |
| `image-slot.js` | `<image-slot>` custom element: renders an author-supplied `src` fallback image, used so admins can swap coach/product/hero imagery via Cloudinary upload. |

### 3.3 Next.js App Router surface

- `app/(public)/{padel,pilates,store,coaches}` — route entries that render the prototype pages.
- `app/(auth)/{login,register,forgot,reset}`, `app/account`, `app/checkout` — member flows.
- `app/api/{auth,health}` — edge/server routes (health check, auth helpers).
- `public/admin/` — internal management panel (`adminApi.js` + `index.html`): orders, products,
  courts, classes, coaches, packs, tournaments, users/wallet, **media library**, and **staff
  accounts** (SUPER_ADMIN-only CRUD over admin logins). This is the single source of truth.
- Cross-cutting: Tailwind v4, TanStack Query + Zustand (React islands), Sentry, CSP headers in
  `next.config.ts` (script/style/img/frame-src incl. Google Maps embed), Playwright e2e.

### 3.4 Deploy note

Both repos deploy from the **`main`** branch. Push to `main` → Vercel (frontend) and Render (backend)
rebuild and redeploy automatically. Never push directly to any other branch expecting it to reach
production.

---

## 4. ⟨BE⟩ Backend architecture

### 4.1 Stack

Kotlin 1.9.25 on **JVM 21**, **Spring Boot 3.5.0**: Web MVC, Data JPA/Hibernate 6, Security,
Bean Validation, Actuator. Persistence in **PostgreSQL** (hosted on Render), schema owned by
**Flyway** (32 migrations through `V32`). JWT via **Auth0 `java-jwt` (RS256)**. Image upload/CDN
via **Cloudinary**. API docs via **SpringDoc / Swagger UI**. Build with Gradle Kotlin DSL;
container image via `Dockerfile`.

### 4.2 Package layout — feature-first, not layer-first

Source lives under `src/main/kotlin/tn/takeoff`, organised **by domain** (each package owns its
controllers, entities, repositories, DTOs):

```
tn/takeoff
├── auth        JWT issue/verify, login, refresh, OTP, password reset
├── users       member profile, identity
├── wallet      TND credit ledger
├── products    catalog + variants
├── orders      cart checkout → orders, status lifecycle
├── courts      padel courts, availability, blocks, bookings
├── classes     pilates schedule, bookings + packs
├── coaches     public coach profiles + Cloudinary photo upload
├── coaching    coaching-inquiry funnel (lead capture)
├── packs       credit packs
├── payments    Konnect payment intents + webhook fulfillment
├── tournaments ladder / events
├── cms         editable site content (hero copy, media assets)
├── common      errors, pagination
├── config      security, JWT props, web/CORS, env diagnostics
└── admin       staff-facing endpoints (audit, auth, per-domain management)
```

### 4.3 Layering & the Gateway pattern

The codebase follows **Controller → Service → Gateway → Repository**:

- **Controller** — HTTP + validation (`@Valid`), no business logic.
- **Service** — transactional business rules (`@Transactional`), orchestration.
- **Gateway** — a domain-owned *interface* describing the persistence operations the domain needs.
- **Repository** — `interface XyzRepository : JpaRepository<…>, XyzGateway`. Spring Data implements
  it; services depend on the **gateway interface**, never on `JpaRepository` directly.

This keeps the domain layer expressed in its own vocabulary and makes the persistence technology a
detail behind an interface (testable, swappable) — a pragmatic ports-and-adapters slant without
ceremony.

### 4.4 Data & migrations

Schema is versioned in `src/main/resources/db/migration` (`V1__…` through `V32__…`); nothing is
auto-DDL'd in production. Notable milestones: product variants (`V17`), payment intents (`V19`),
pilates class types + comprehensive seed data (`V21–V22`), OTP auth (`V29`), expenses (`V30`),
rolling session seed (`V31`), coach photos + product images (`V32`).

A companion `PilatesScheduleSeeder` (opt-out via `SEED_PILATES_SCHEDULE=false`) runs on every
startup and rolls a full weekly reformer/mat schedule forward 5 weeks from the current date, so the
public timetable is never empty in a fresh environment.

All image uploads (coaches, products, CMS) go through `MediaUploadService` → **Cloudinary**, so
photo URLs stored in the DB are permanent CDN links and survive server restarts and redeployments.

---

## 5. Security model

- **Two Spring Security filter chains** (`config/SecurityConfig.kt`):
  - `adminFilterChain` guards `/api/v1/admin/**` — everything authenticated except admin login.
  - `memberFilterChain` guards the rest with an explicit **public allowlist**: `GET` on
    products/courts/coaches/content, the class schedule/packs, the coaching-inquiry `POST`, and the
    Konnect `POST /payments/webhook` (verified by signature, not JWT).
- Both chains are **stateless** (`SessionCreationPolicy.STATELESS`), **CSRF disabled** (token auth,
  no cookies-as-credentials), CORS driven by `CORS_ALLOWED_ORIGINS` env var, each with a
  **custom JWT filter** ahead of `UsernamePasswordAuthenticationFilter`.
- **JWT is RS256** (`auth/JwtService.kt`): an RSA **private key signs**, the **public key verifies**.
  Keys are supplied as PEM strings via environment variables (`JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY`) —
  they are **never** committed. Missing keys fail token issuance loudly by design.
- Passwords hashed with **BCrypt (strength 12)**.
- **Payment integrity** is server-side: prices and delivery fees are re-derived from the database at
  order placement (client-supplied amounts are not trusted), and Konnect fulfillment is driven by a
  signature-verified webhook, not by the browser.

> **Secrets discipline:** database credentials, JWT keys, Cloudinary credentials, and payment keys
> live only in host environment variables. They must never be committed to the repository.

---

## 6. Payments (Konnect)

Card payments use a **server-initiated intent** model: the backend creates a `payment_intent`
(`V19`), calls Konnect to get a hosted payment URL, redirects the user, and later receives a
**signed webhook** that transitions the intent to `PAID`/`FAILED` and dispatches fulfillment
(confirm order / top up wallet / activate pack / confirm court booking). When Konnect credentials
are absent the service runs in a **stub mode** for local/dev, so the rest of the flow is testable
without live payment keys.

---

## 7. Environments & delivery

| Concern | Frontend | Backend |
|---|---|---|
| Host | Vercel | Render (Web Service + managed PostgreSQL) |
| Deploy trigger | push to **`main`** branch | push to **`main`** branch |
| Branch model | `main` → auto-deploy | `main` → auto-deploy |
| CI | GitHub Actions: lint, typecheck, build, CodeQL, Playwright e2e | GitHub Actions: build/test, CodeQL, image build/push |
| Media | — | Cloudinary (permanent CDN URLs) |
| Errors | Sentry | (planned) |
| Dependencies | Dependabot: npm (grouped) + github-actions | Dependabot: gradle + github-actions |

---

## 8. Notable engineering decisions & trade-offs

1. **Prototype-as-production frontend.** Marketing pages ship as `.dc.html` behind Next.js instead
   of being rewritten in React. *Pro:* zero-loss fidelity from the design prototype, fast iteration,
   Next.js still owns hosting/routing/CSP/API. *Con:* two rendering paradigms in one app and an
   asset-rewrite allowlist that must be kept in sync. A future consolidation onto React is the
   obvious next step if the marketing pages start needing app-grade state.
2. **RS256 over HS256 for JWT.** Asymmetric keys mean the signing secret never leaves the issuer and
   verifiers only need the public key — a deliberately stronger posture than a shared HMAC secret.
3. **Feature-first packages + Gateway interfaces.** Optimises for domain cohesion and testability
   over the conventional `controllers/`/`services/` split.
4. **Server-authoritative money.** Prices, fees, and payment state are never trusted from the client;
   they are recomputed and webhook-verified server-side.
5. **Cloudinary for all image storage.** All uploads (coach photos, product images, CMS media) go
   through `MediaUploadService` → Cloudinary, ensuring URLs stored in the DB are permanent CDN links
   that survive any server restart or redeployment.

### Migration note

The backend was **migrated from an earlier Node/NestJS + Fastify + Drizzle ORM stack** to the
current Spring Boot/Kotlin one. If you find a stray reference to Node, Drizzle, or ports 3000/3001
in older scripts, it is a leftover to be scrubbed rather than a live path.

---

## 9. Known issues & roadmap

An honest account of what is wired end-to-end versus what is scaffolded.

**Admin → public propagation (audited).**

| Domain | Admin edits reach the public site? |
|---|---|
| Coaches (incl. photos) | ✅ Yes — public pages fetch coach records and bind `photoUrl`. |
| Courts / availability | ✅ Yes. |
| Products / store | ✅ Yes. |
| Classes / pilates schedule | ✅ Yes (auto-seeded rolling 5-week schedule). |
| CMS site content | ⚠️ Partial — only the **padel** and **pilates** pages read `content.page`. Coaches, store, and gateway pages render hard-coded copy; editing them in the CMS has no effect yet. |
| Tournaments | ⚠️ Disconnected — admin tournaments module persists data but the public padel page reads from CMS, not the tournaments API. |
| Packs / pilates plans | ⚠️ Disconnected — public pilates page shows hard-coded plan prices rather than admin-managed pack types. |

**Payments.** The Konnect flow is intent + signed-webhook complete but runs in **stub mode** until
live credentials (`KONNECT_API_KEY`, `KONNECT_WEBHOOK_SECRET`) are set. Until then card checkout
falls through to a local success screen.

**Test coverage.** Thin. The Gateway pattern makes services unit-testable, but domain services and
the payment/fulfillment path are the priority gap; Playwright e2e on the frontend covers happy paths
only.

**Roadmap (near-term).** Wire CMS into remaining public pages · connect public padel page to
tournaments API and pilates page to admin-managed packs · finish Konnect activation · grow
service-layer and payment tests.

---

## 10. Local development

**Backend**
```bash
# requires JDK 21 + a PostgreSQL instance; JWT keys + DB creds via env
cp .env.example .env       # fill in DATABASE_URL, JWT_PRIVATE_KEY, JWT_PUBLIC_KEY, CLOUDINARY_URL
./gradlew bootRun          # API on :8080, Swagger at /swagger-ui.html
./gradlew test             # JUnit 5
```

**Frontend**
```bash
npm install
npm run dev                # Next.js dev server
# point the client at an API:  NEXT_PUBLIC_API_URL=http://localhost:8080
npm run typecheck && npm run lint
npm run test:e2e           # Playwright
```

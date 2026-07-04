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

The system is a classic **decoupled SPA-ish frontend + REST API backend**, deployed as two
independently shipped services.

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
                          │  take-off-api  (Railway → OVH planned)   │
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
                          │ (managed)      │      │ (image upload/CDN)│
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
| `api-client.js` | Fetch wrapper. Base URL from `window.TAKEOFF_API_URL`; attaches Bearer JWT; transparent **access/refresh token** rotation via `/api/v1/auth/refresh`; degrades to a read-only "offline" mode when no API URL is configured. |
| `auth.js` | Account drawer, login/register/profile, order & booking history. Output is HTML-escaped via `esc()` to prevent stored XSS. |
| `cart.js` | Cart + multi-step checkout (delivery/pickup, payment method, order placement, Konnect redirect prep). |
| `menu.js` | Shared nav + footer (embedded Google Map to the Sfax location). |
| `image-slot.js` | `<image-slot>` custom element: renders an author-supplied `src` (or a design-tool sidecar in dev), used so admins can swap coach/product/hero imagery. |

### 3.3 Next.js App Router surface

- `app/(public)/{padel,pilates,store,coaches}` — route entries that render the prototype pages.
- `app/(auth)/{login,register,forgot,reset}`, `app/account`, `app/checkout` — member flows.
- `app/api/{auth,health}` — edge/server routes (health check, auth helpers).
- `public/admin/` — internal management panel (`adminApi.js` + `index.html`): orders, products,
  courts, classes, coaches, packs, tournaments, users/wallet, **media library**, and **staff
  accounts** (SUPER_ADMIN-only CRUD over admin logins). This is the single source of truth; an older
  divergent copy under a top-level `admin/` was reconciled into `public/admin/` so Next.js serves it
  statically at `/admin`.
- Cross-cutting: Tailwind v4, TanStack Query + Zustand (React islands), Sentry, CSP headers in
  `next.config.ts` (script/style/img/frame-src incl. Google Maps embed), Playwright e2e.

### 3.4 Deploy note (learned the hard way)

Vercel builds this project from the **`production` branch**, not `main`. `main` is the integration
branch; shipping requires promoting `main → production`. See `README`/CI for the exact flow.

---

## 4. ⟨BE⟩ Backend architecture

### 4.1 Stack

Kotlin 1.9.25 on **JVM 21**, **Spring Boot 3.5.0**: Web MVC, Data JPA/Hibernate 6, Security,
Bean Validation, Actuator. Persistence in **PostgreSQL**, schema owned by **Flyway** (21 migrations
through `V21`). JWT via **Auth0 `java-jwt` (RS256)**. Image upload/CDN via **Cloudinary**. API docs
via **SpringDoc / Swagger UI**. Build with Gradle Kotlin DSL; container image via `Dockerfile`.

### 4.2 Package layout — feature-first, not layer-first

Source lives under `src/main/kotlin/tn/takeoff`, organised **by domain** (each package owns its
controllers, entities, repositories, DTOs):

```
tn/takeoff
├── auth        JWT issue/verify, login, refresh, password reset
├── users       member profile, identity
├── wallet      TND credit ledger
├── products    catalog + variants
├── orders      cart checkout → orders, status lifecycle
├── courts      padel courts, availability, blocks, bookings
├── classes     pilates schedule + packs
├── coaches     public coach profiles
├── coaching    coaching-inquiry funnel (lead capture)
├── packs       credit packs
├── payments    Konnect payment intents + webhook fulfillment
├── tournaments ladder / events
├── cms         editable site content (hero copy, etc.)
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

Schema is versioned in `src/main/resources/db/migration` (`V1__…` … `V21__…`); nothing is
auto-DDL'd in production. Recent migrations show the domain maturing: product variants (`V17`),
order enhancements (`V18`), payment intents (`V19`), nullable product FK on order items (`V20`),
seed pilates class types (`V21`, idempotent — only fires when `class_types` is empty). A companion
`PilatesScheduleSeeder` (opt-out via `SEED_PILATES_SCHEDULE=false`) rolls a full weekly reformer/mat
schedule forward from the current week so the public timetable is never empty in a fresh environment.

---

## 5. Security model

- **Two Spring Security filter chains** (`config/SecurityConfig.kt`):
  - `adminFilterChain` guards `/api/v1/admin/**` — everything authenticated except admin login.
  - `memberFilterChain` guards the rest with an explicit **public allowlist**: `GET` on
    products/courts/coaches/content, the class schedule/packs, the coaching-inquiry `POST`, and the
    Konnect `POST /payments/webhook` (verified by signature, not JWT).
- Both chains are **stateless** (`SessionCreationPolicy.STATELESS`), **CSRF disabled** (token auth,
  no cookies-as-credentials), CORS driven by config, each with a **custom JWT filter** ahead of
  `UsernamePasswordAuthenticationFilter`.
- **JWT is RS256** (`auth/JwtService.kt`): an RSA **private key signs**, the **public key verifies**.
  Keys are supplied as PEM via environment variables — they are **never** committed. Missing keys
  fail token issuance loudly by design.
- Passwords hashed with **BCrypt (strength 12)**.
- **Payment integrity** is server-side: prices and delivery fees are re-derived from the database at
  order placement (client-supplied amounts are not trusted), and Konnect fulfillment is driven by a
  signature-verified webhook, not by the browser.

> **Secrets discipline:** database credentials and JWT keys live only in host environment variables
> (Railway/OVH). They must never be committed. Rotating a leaked credential is an ops task, not a
> code change.

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
| Host | Vercel | Railway (managed PostgreSQL); OVH VPS is the planned near-term target |
| Deploy trigger | push to **`production`** branch | container image build + deploy |
| Branch model | `dev` → `main` (integration) → `production` (live) | `dev` → `main` |
| CI | GitHub Actions: lint, typecheck, build, CodeQL, Playwright e2e | GitHub Actions: build/test, CodeQL, image build/push, staged deploy |
| Media | — | Cloudinary |
| Errors | Sentry | (planned) |
| Dependencies | Dependabot: npm (grouped) + github-actions | Dependabot: **gradle** + github-actions |

---

## 8. Notable engineering decisions & trade-offs

1. **Prototype-as-production frontend.** Marketing pages ship as `.dc.html` behind Next.js instead
   of being rewritten in React. *Pro:* zero-loss fidelity from the design prototype, fast iteration,
   Next.js still owns hosting/routing/CSP/API. *Con:* two rendering paradigms in one app and an
   asset-rewrite allowlist that must be kept in sync (documented, with a guard). A future
   consolidation onto React is the obvious next step if the marketing pages start needing app-grade
   state.
2. **RS256 over HS256 for JWT.** Asymmetric keys mean the signing secret never leaves the issuer and
   verifiers only need the public key — a deliberately stronger posture than a shared HMAC secret.
3. **Feature-first packages + Gateway interfaces.** Optimises for domain cohesion and testability
   over the conventional `controllers/`/`services/` split.
4. **Server-authoritative money.** Prices, fees, and payment state are never trusted from the client;
   they are recomputed and webhook-verified server-side.
5. **Free-tier-first infra, with explicit paid triggers.** See `INFRASTRUCTURE.md` for the cost model.

### Migration note (why some things look the way they do)

The backend was **migrated from an earlier Node/NestJS + Fastify + Drizzle ORM stack** to the
current Spring Boot/Kotlin one. The `drizzle/` folder and npm-based Dependabot config were removed
as part of that cleanup; if you find a stray reference to Node, Drizzle, or ports 3000/3001 in older
scripts, it is a leftover to be scrubbed rather than a live path.

---

## 9. Known issues & roadmap

An honest account of what is wired end-to-end versus what is scaffolded. Kept here so the gaps are
visible rather than surprising.

**Admin → public propagation (audited).**

| Domain | Admin edits reach the public site? |
|---|---|
| Coaches (incl. photos) | ✅ Yes — public pages fetch coach records and bind `photoUrl`. |
| Courts / availability | ✅ Yes. |
| Products / store | ✅ Yes. |
| Classes / pilates schedule | ✅ Yes (now seeded so it renders full). |
| CMS site content | ⚠️ Partial — only the **padel** and **pilates** pages read `content.page`. The coaches, store, and gateway pages still render hard-coded copy; editing them in the CMS has no effect yet. |
| Tournaments | ⚠️ Disconnected — the admin tournaments module persists data, but the public padel page reads tournament copy from the CMS, not the tournaments API. |
| Packs / pilates plans | ⚠️ Disconnected — the public pilates page shows hard-coded plan prices (300 / 420 DT) rather than the admin-managed pack types. |

**Payments.** The Konnect flow is intent + signed-webhook complete but runs in **stub mode** until
live credentials (`KONNECT_API_KEY`, `KONNECT_WEBHOOK_SECRET`) are set — see `project-konnect-todo`
and §6. Until then card checkout falls through to a local success screen.

**Test coverage.** Thin. The Gateway pattern makes services unit-testable, but domain services and
the payment/fulfillment path are the priority gap; Playwright e2e on the frontend covers happy paths
only.

**Frontend build note.** Two rendering paradigms coexist (§3.1, §8): the `.dc.html` prototype pages
and the React App Router. The asset-rewrite allowlist in `lib/prototype/serve.ts` must be kept in
sync when shared assets are added.

**Roadmap (near-term).** Wire CMS into the remaining public pages · connect the public padel page to
the tournaments API and the pilates page to admin-managed packs · finish Konnect activation · grow
service-layer and payment tests.

---

## 10. Local development

**Backend**
```bash
# requires JDK 21 + a PostgreSQL instance; JWT keys + DB creds via env
./gradlew bootRun          # API on :8080, Swagger at /swagger-ui.html
./gradlew test             # JUnit 5
```

**Frontend**
```bash
npm install
npm run dev                # Next.js dev server
# point the client at an API:  window.TAKEOFF_API_URL = "http://localhost:8080"
npm run typecheck && npm run lint
npm run test:e2e           # Playwright
```

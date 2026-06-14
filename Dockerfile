# ============================================================
# Take Off API — multi-stage Docker build
# Stage 1: deps    — install all dependencies (cached)
# Stage 2: builder — compile TypeScript
# Stage 3: runtime — Alpine minimal image, non-root user
# ============================================================

# ---------- Stage 1: deps ----------
FROM node:20-alpine AS deps
WORKDIR /app

# Install pnpm
RUN corepack enable && corepack prepare pnpm@latest --activate

COPY package.json pnpm-lock.yaml* ./
RUN pnpm install --frozen-lockfile --prod=false

# ---------- Stage 2: builder ----------
FROM node:20-alpine AS builder
WORKDIR /app

RUN corepack enable && corepack prepare pnpm@latest --activate

COPY --from=deps /app/node_modules ./node_modules
COPY . .

RUN pnpm build

# ---------- Stage 3: runtime ----------
FROM node:20-alpine AS runtime
WORKDIR /app

# Security: run as non-root
RUN addgroup -S takeoff && adduser -S takeoff -G takeoff

# Only production dependencies
COPY package.json pnpm-lock.yaml* ./
RUN corepack enable && corepack prepare pnpm@latest --activate \
    && pnpm install --frozen-lockfile --prod \
    && pnpm store prune

COPY --from=builder /app/dist ./dist
COPY --from=builder /app/drizzle ./drizzle

# Switch to non-root user
USER takeoff

EXPOSE 3001

# Healthcheck — Caddy / deploy script polls this
HEALTHCHECK --interval=10s --timeout=3s --start-period=15s --retries=5 \
  CMD wget -qO- http://localhost:3001/health || exit 1

CMD ["node", "dist/main"]

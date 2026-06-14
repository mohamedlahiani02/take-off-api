import { Module, Controller, Get } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { ThrottlerModule } from '@nestjs/throttler';
import { LoggerModule } from 'nestjs-pino';
import { DrizzleModule } from './infrastructure/drizzle.module';
import { RedisModule } from './infrastructure/redis.module';
import { IdentityModule } from './modules/identity/identity.module';
import { PadelModule } from './modules/padel/padel.module';
import { PilatesModule } from './modules/pilates/pilates.module';
import { CatalogModule } from './modules/catalog/catalog.module';
import { OrdersModule } from './modules/orders/orders.module';
import { PaymentsModule } from './modules/payments/payments.module';
import { WalletModule } from './modules/wallet/wallet.module';
import { RankingModule } from './modules/ranking/ranking.module';
import { CoachingModule } from './modules/coaching/coaching.module';
import { ContentModule } from './modules/content/content.module';
import { NotificationsModule } from './modules/notifications/notifications.module';

/** Minimal health controller — outside /v1 prefix so Caddy can poll it cheaply. */
@Controller('health')
class HealthController {
  @Get()
  check(): { ok: boolean; ts: string } {
    return { ok: true, ts: new Date().toISOString() };
  }
}

@Module({
  imports: [
    // ── Config (loads .env) ──────────────────────────────────
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: ['.env.local', '.env'],
      cache: true,
    }),

    // ── Structured logging (Pino) ────────────────────────────
    LoggerModule.forRoot({
      pinoHttp: {
        transport:
          process.env['NODE_ENV'] !== 'production'
            ? { target: 'pino-pretty', options: { singleLine: true } }
            : undefined,
        redact: ['req.headers.authorization', 'req.headers.cookie'],
        serializers: {
          req(req: { method: string; url: string }) {
            return { method: req.method, url: req.url };
          },
        },
      },
    }),

    // ── Rate limiting (Redis-backed via ThrottlerModule) ──────
    ThrottlerModule.forRoot([
      {
        name: 'short',
        ttl: 1000,
        limit: 10,
      },
      {
        name: 'medium',
        ttl: 60_000,
        limit: 100,
      },
    ]),

    // ── Infrastructure ───────────────────────────────────────
    DrizzleModule,
    RedisModule,

    // ── Feature modules ──────────────────────────────────────
    IdentityModule,
    PadelModule,
    PilatesModule,
    CatalogModule,
    OrdersModule,
    PaymentsModule,
    WalletModule,
    RankingModule,
    CoachingModule,
    ContentModule,
    NotificationsModule,
  ],
  controllers: [HealthController],
})
export class AppModule {}

import { Injectable, OnModuleDestroy, OnModuleInit, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import Redis from 'ioredis';

/**
 * RedisService — thin wrapper around ioredis.
 * Exposes `client` for direct ioredis access.
 *
 * Rate limiting: ThrottlerModule is configured to use this client.
 * Idempotency: IdempotencyInterceptor stores response hashes here.
 * Refresh tokens: stored hashed with TTL=30d (see IdentityService).
 *
 * See ARCHITECTURE.md §12 for rate limit specs.
 */
@Injectable()
export class RedisService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(RedisService.name);
  public client!: Redis;

  constructor(private readonly config: ConfigService) {}

  onModuleInit(): void {
    const url = this.config.getOrThrow<string>('REDIS_URL');
    this.client = new Redis(url, {
      maxRetriesPerRequest: 3,
      enableReadyCheck: true,
      lazyConnect: false,
    });

    this.client.on('connect', () => this.logger.log('Redis connected'));
    this.client.on('error', (err: Error) => this.logger.error({ err }, 'Redis error'));
  }

  async onModuleDestroy(): Promise<void> {
    await this.client.quit();
    this.logger.log('Redis connection closed');
  }
}

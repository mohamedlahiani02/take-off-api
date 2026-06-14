import { Module, Global } from '@nestjs/common';
import { RedisService } from './redis.service';

/**
 * RedisModule — global singleton wrapping an ioredis client.
 * Used by: ThrottlerModule storage, IdempotencyInterceptor, session cache.
 */
@Global()
@Module({
  providers: [RedisService],
  exports: [RedisService],
})
export class RedisModule {}

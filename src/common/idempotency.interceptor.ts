import {
  CallHandler,
  ExecutionContext,
  Injectable,
  NestInterceptor,
  HttpException,
  HttpStatus,
  Logger,
} from '@nestjs/common';
import { Observable, from, of } from 'rxjs';
import { switchMap, tap } from 'rxjs/operators';
import { FastifyRequest, FastifyReply } from 'fastify';
import { RedisService } from '../infrastructure/redis.service';

const IDEMPOTENCY_HEADER = 'idempotency-key';
const CACHE_TTL_SECONDS = 86_400; // 24 hours — ARCHITECTURE.md §7 §11

/**
 * Idempotency interceptor — reads the `Idempotency-Key` header on every
 * mutating request (POST/PATCH/DELETE) and caches the serialised response
 * in Redis for 24 h. Subsequent calls with the same key return the cached
 * response without re-running the handler.
 *
 * Required on: bookings, pack purchases, orders, wallet top-ups, payments.
 * See ARCHITECTURE.md §7 (key cross-cutting concerns).
 */
@Injectable()
export class IdempotencyInterceptor implements NestInterceptor {
  private readonly logger = new Logger(IdempotencyInterceptor.name);

  constructor(private readonly redis: RedisService) {}

  intercept(context: ExecutionContext, next: CallHandler): Observable<unknown> {
    const http = context.switchToHttp();
    const request = http.getRequest<FastifyRequest>();
    const reply = http.getResponse<FastifyReply>();

    // Only apply to unsafe methods
    if (!['POST', 'PATCH', 'PUT', 'DELETE'].includes(request.method)) {
      return next.handle();
    }

    const idemKey = request.headers[IDEMPOTENCY_HEADER] as string | undefined;
    if (!idemKey) {
      return next.handle();
    }

    const cacheKey = `idempotency:${idemKey}`;

    return from(this.redis.client.get(cacheKey)).pipe(
      switchMap((cached) => {
        if (cached) {
          this.logger.debug({ idemKey }, 'Idempotency cache hit — returning cached response');
          const parsed = JSON.parse(cached) as { status: number; body: unknown };
          reply.status(parsed.status);
          reply.header('X-Idempotency-Replayed', 'true');
          return of(parsed.body);
        }

        return next.handle().pipe(
          tap((body: unknown) => {
            const status = reply.statusCode;
            // Only cache successful responses
            if (status >= 200 && status < 300) {
              const payload = JSON.stringify({ status, body });
              void this.redis.client.set(cacheKey, payload, 'EX', CACHE_TTL_SECONDS);
            }
          }),
        );
      }),
    );
  }
}

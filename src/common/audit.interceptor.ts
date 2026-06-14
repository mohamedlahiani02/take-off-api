import {
  CallHandler,
  ExecutionContext,
  Injectable,
  NestInterceptor,
  Logger,
} from '@nestjs/common';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { FastifyRequest } from 'fastify';

/**
 * Audit interceptor — placeholder.
 * TODO: on every successful mutating response, append a row to the
 * `identity.audit_event` table with: actor_id, action, resource_type,
 * resource_id, payload snapshot, ip, user_agent.
 *
 * See ARCHITECTURE.md §12 (Security — audit log).
 */
@Injectable()
export class AuditInterceptor implements NestInterceptor {
  private readonly logger = new Logger(AuditInterceptor.name);

  intercept(context: ExecutionContext, next: CallHandler): Observable<unknown> {
    const request = context.switchToHttp().getRequest<FastifyRequest>();

    return next.handle().pipe(
      tap(() => {
        // TODO: implement audit log persistence
        // const user = request['user'] as { id: string } | undefined;
        // const action = `${request.method} ${request.url}`;
        // await this.auditService.record({ actorId: user?.id, action, ... });
        this.logger.verbose({ method: request.method, url: request.url }, 'AuditInterceptor noop');
      }),
    );
  }
}

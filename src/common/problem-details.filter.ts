import {
  ArgumentsHost,
  Catch,
  ExceptionFilter,
  HttpException,
  HttpStatus,
  Logger,
} from '@nestjs/common';
import { FastifyReply, FastifyRequest } from 'fastify';
import { v4 as uuidv4 } from 'uuid';

/**
 * RFC 7807 ProblemDetails exception filter.
 * All API errors are returned as application/problem+json with:
 *   type, title, status, code (domain-specific), detail, trace_id
 *
 * Domain-specific codes follow the pattern: takeoff.<module>.<snake_error>
 * Example: takeoff.booking.slot_taken
 */
@Catch()
export class ProblemDetailsFilter implements ExceptionFilter {
  private readonly logger = new Logger(ProblemDetailsFilter.name);

  catch(exception: unknown, host: ArgumentsHost): void {
    const ctx = host.switchToHttp();
    const reply = ctx.getResponse<FastifyReply>();
    const request = ctx.getRequest<FastifyRequest>();

    const traceId = (request.headers['x-trace-id'] as string | undefined) ?? uuidv4();

    let status = HttpStatus.INTERNAL_SERVER_ERROR;
    let title = 'Internal Server Error';
    let code = 'takeoff.internal_error';
    let detail = 'An unexpected error occurred. Our team has been notified.';

    if (exception instanceof HttpException) {
      status = exception.getStatus();
      const response = exception.getResponse();

      if (typeof response === 'object' && response !== null) {
        const r = response as Record<string, unknown>;
        title = (r['error'] as string | undefined) ?? exception.message;
        detail =
          Array.isArray(r['message'])
            ? (r['message'] as string[]).join('; ')
            : (r['message'] as string | undefined) ?? detail;
        code = (r['code'] as string | undefined) ?? httpStatusToCode(status);
      } else if (typeof response === 'string') {
        title = response;
        detail = response;
        code = httpStatusToCode(status);
      }
    } else if (exception instanceof Error) {
      this.logger.error({ err: exception, traceId }, 'Unhandled exception');
      detail = `${detail} (trace_id: ${traceId})`;
    }

    const body = {
      type: `https://takeoff.tn/errors/${code.replace(/\./g, '/')}`,
      title,
      status,
      code,
      detail,
      trace_id: traceId,
    };

    void reply
      .status(status)
      .header('Content-Type', 'application/problem+json')
      .send(body);
  }
}

function httpStatusToCode(status: number): string {
  const map: Record<number, string> = {
    400: 'takeoff.bad_request',
    401: 'takeoff.unauthorized',
    403: 'takeoff.forbidden',
    404: 'takeoff.not_found',
    409: 'takeoff.conflict',
    422: 'takeoff.unprocessable',
    429: 'takeoff.rate_limited',
    500: 'takeoff.internal_error',
  };
  return map[status] ?? 'takeoff.error';
}

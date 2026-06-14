import { createParamDecorator, ExecutionContext } from '@nestjs/common';
import { FastifyRequest } from 'fastify';

export interface AuthenticatedUser {
  id: string;
  email: string;
  name: string;
  tracks: string[];
  role: 'USER' | 'STAFF' | 'ADMIN';
}

/**
 * Parameter decorator that extracts the authenticated user from the Fastify
 * request (set by JwtStrategy after token validation).
 *
 * Usage:
 *   @Get('me')
 *   @UseGuards(JwtAuthGuard)
 *   getMe(@CurrentUser() user: AuthenticatedUser) { ... }
 */
export const CurrentUser = createParamDecorator(
  (_data: unknown, ctx: ExecutionContext): AuthenticatedUser => {
    const request = ctx.switchToHttp().getRequest<FastifyRequest>();
    return request['user'] as AuthenticatedUser;
  },
);

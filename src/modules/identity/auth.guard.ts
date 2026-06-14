import { Injectable, ExecutionContext } from '@nestjs/common';
import { AuthGuard } from '@nestjs/passport';
import { Observable } from 'rxjs';

/**
 * JwtAuthGuard — apply to any controller/route that requires authentication.
 *
 * Usage:
 *   @UseGuards(JwtAuthGuard)
 *   @Get('me')
 *   getMe(@CurrentUser() user: AuthenticatedUser) { ... }
 */
@Injectable()
export class JwtAuthGuard extends AuthGuard('jwt') {
  override canActivate(context: ExecutionContext): boolean | Promise<boolean> | Observable<boolean> {
    return super.canActivate(context);
  }
}

/**
 * LocalAuthGuard — used only on POST /auth/login.
 * Runs LocalStrategy which validates email + password.
 */
@Injectable()
export class LocalAuthGuard extends AuthGuard('local') {}

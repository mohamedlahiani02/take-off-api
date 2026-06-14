import { Injectable, UnauthorizedException } from '@nestjs/common';
import { PassportStrategy } from '@nestjs/passport';
import { Strategy } from 'passport-local';
import { IdentityService } from './identity.service';
import { AuthenticatedUser } from '../../common/current-user.decorator';

/**
 * Local strategy — email + password validation used by /auth/login.
 * Delegates credential verification to IdentityService.validateCredentials.
 */
@Injectable()
export class LocalStrategy extends PassportStrategy(Strategy, 'local') {
  constructor(private readonly identity: IdentityService) {
    super({ usernameField: 'email', passwordField: 'password' });
  }

  async validate(email: string, password: string): Promise<AuthenticatedUser> {
    const user = await this.identity.validateCredentials(email, password);
    if (!user) {
      throw new UnauthorizedException({ code: 'takeoff.identity.invalid_credentials' });
    }
    return user;
  }
}

import { Injectable, UnauthorizedException } from '@nestjs/common';
import { PassportStrategy } from '@nestjs/passport';
import { ExtractJwt, Strategy } from 'passport-jwt';
import { ConfigService } from '@nestjs/config';
import { readFileSync } from 'fs';
import { AuthenticatedUser } from '../../common/current-user.decorator';

interface JwtPayload {
  sub: string;
  email: string;
  name: string;
  tracks: string[];
  role: 'USER' | 'STAFF' | 'ADMIN';
  iat?: number;
  exp?: number;
}

/**
 * JWT strategy — RS256 (asymmetric keys).
 * Public key path configured via JWT_PUBLIC_KEY_PATH env var.
 * Token extracted from Authorization: Bearer header.
 *
 * See ARCHITECTURE.md §12 (Auth — JWT RS256).
 */
@Injectable()
export class JwtStrategy extends PassportStrategy(Strategy, 'jwt') {
  constructor(config: ConfigService) {
    const publicKeyPath = config.getOrThrow<string>('JWT_PUBLIC_KEY_PATH');
    const publicKey = readFileSync(publicKeyPath, 'utf-8');

    super({
      jwtFromRequest: ExtractJwt.fromAuthHeaderAsBearerToken(),
      ignoreExpiration: false,
      secretOrKey: publicKey,
      algorithms: ['RS256'],
    });
  }

  validate(payload: JwtPayload): AuthenticatedUser {
    if (!payload.sub) {
      throw new UnauthorizedException('Invalid token payload');
    }

    return {
      id: payload.sub,
      email: payload.email,
      name: payload.name,
      tracks: payload.tracks,
      role: payload.role,
    };
  }
}

import { Injectable, Logger, ConflictException, UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import * as bcrypt from 'bcrypt';
import { readFileSync } from 'fs';
import { DrizzleService } from '../../infrastructure/drizzle.service';
import { RedisService } from '../../infrastructure/redis.service';
import { AuthenticatedUser } from '../../common/current-user.decorator';
import { RegisterDto } from './dto/register.dto';
import { UpdateMeDto } from './dto/update-me.dto';
import { LevelSurveyDto } from './dto/level-survey.dto';

const BCRYPT_ROUNDS = 12;
const REFRESH_TTL_SECONDS = 30 * 24 * 60 * 60; // 30 days

@Injectable()
export class IdentityService {
  private readonly logger = new Logger(IdentityService.name);
  private privateKey!: string;

  constructor(
    private readonly drizzle: DrizzleService,
    private readonly redis: RedisService,
    private readonly jwt: JwtService,
    private readonly config: ConfigService,
  ) {
    const keyPath = this.config.getOrThrow<string>('JWT_PRIVATE_KEY_PATH');
    this.privateKey = readFileSync(keyPath, 'utf-8');
  }

  /**
   * Register a new user.
   * - Checks email uniqueness
   * - Hashes password with bcrypt (BCRYPT_ROUNDS=12)
   * - Inserts into identity.users
   * - Queues a verification email via outbox
   * TODO: implement full persistence
   */
  async register(dto: RegisterDto): Promise<{ userId: string }> {
    // TODO: check identity.users WHERE email = dto.email → throw ConflictException if found
    this.logger.log({ email: dto.email }, 'Registering user');

    const _passwordHash = await bcrypt.hash(dto.password, BCRYPT_ROUNDS);
    // TODO: INSERT INTO identity.users (id, email, password_hash, name, phone, tracks, locale)
    // TODO: INSERT INTO identity.outbox_event (eventType='UserRegistered', payload={userId, email})

    throw new Error('IdentityService.register not implemented — TODO');
  }

  /**
   * Validate email + password credentials (used by LocalStrategy).
   * Returns null on invalid credentials (rate-limited upstream by ThrottlerGuard).
   * TODO: implement
   */
  async validateCredentials(
    email: string,
    password: string,
  ): Promise<AuthenticatedUser | null> {
    // TODO: SELECT * FROM identity.users WHERE email = $1
    // TODO: bcrypt.compare(password, user.password_hash)
    this.logger.debug({ email }, 'Validating credentials');
    void password;
    return null;
  }

  /**
   * Issue access + refresh token pair.
   * Refresh token stored hashed in Redis with 30-day TTL + device fingerprint.
   * TODO: implement
   */
  async issueTokens(user: AuthenticatedUser): Promise<{ accessToken: string; refreshToken: string }> {
    const accessToken = this.jwt.sign(
      { sub: user.id, email: user.email, name: user.name, tracks: user.tracks, role: user.role },
      {
        algorithm: 'RS256',
        privateKey: this.privateKey,
        expiresIn: this.config.get<number>('JWT_ACCESS_TTL', 900),
      },
    );

    // TODO: generate opaque refresh token, hash it (Argon2/bcrypt), store in Redis
    // Key pattern: refresh:{userId}:{deviceId}  value: hashed token  TTL: 30d
    const refreshToken = 'TODO_IMPLEMENT_REFRESH_TOKEN';
    void REFRESH_TTL_SECONDS;

    return { accessToken, refreshToken };
  }

  /**
   * Rotate refresh token — invalidate old, issue new.
   * TODO: implement
   */
  async rotateRefreshToken(
    _oldRefreshToken: string,
  ): Promise<{ accessToken: string; refreshToken: string }> {
    throw new UnauthorizedException({ code: 'takeoff.identity.invalid_refresh_token' });
  }

  /**
   * Invalidate all refresh tokens for a user (logout all devices).
   * TODO: scan Redis for refresh:{userId}:* and delete.
   */
  async revokeAllSessions(_userId: string): Promise<void> {
    // TODO: SCAN + DEL refresh:{userId}:*
    this.logger.log({ userId: _userId }, 'Revoking all sessions (TODO)');
  }

  /**
   * Return full user profile including wallet balance (derived from ledger sum).
   * TODO: implement
   */
  async getMe(_userId: string): Promise<Record<string, unknown>> {
    // TODO: SELECT users.*, SUM(wallet_entry.delta_tnd) AS balance FROM identity.users
    //       LEFT JOIN wallet.entry ON entry.user_id = users.id
    //       WHERE users.id = $1
    throw new Error('IdentityService.getMe not implemented — TODO');
  }

  /**
   * Update mutable profile fields.
   * TODO: implement
   */
  async updateMe(_userId: string, _dto: UpdateMeDto): Promise<Record<string, unknown>> {
    throw new Error('IdentityService.updateMe not implemented — TODO');
  }

  /**
   * Seed padel level from self-declared survey.
   * See ARCHITECTURE.md §11.3 (ELO — initial points).
   * TODO: implement
   */
  async submitLevelSurvey(
    _userId: string,
    _dto: LevelSurveyDto,
  ): Promise<{ newLevel: number; points: number }> {
    throw new Error('IdentityService.submitLevelSurvey not implemented — TODO');
  }
}

import {
  Controller,
  Post,
  Get,
  Patch,
  Body,
  UseGuards,
  HttpCode,
  HttpStatus,
  Req,
  Res,
} from '@nestjs/common';
import {
  ApiTags,
  ApiOperation,
  ApiBearerAuth,
  ApiCreatedResponse,
  ApiOkResponse,
  ApiUnauthorizedResponse,
  ApiConflictResponse,
} from '@nestjs/swagger';
import { Throttle } from '@nestjs/throttler';
import { FastifyRequest, FastifyReply } from 'fastify';
import { IdentityService } from './identity.service';
import { JwtAuthGuard, LocalAuthGuard } from './auth.guard';
import { CurrentUser, AuthenticatedUser } from '../../common/current-user.decorator';
import { RegisterDto } from './dto/register.dto';
import { UpdateMeDto } from './dto/update-me.dto';
import { LevelSurveyDto } from './dto/level-survey.dto';

@ApiTags('Identity')
@Controller()
export class IdentityController {
  constructor(private readonly identity: IdentityService) {}

  // ── Registration ─────────────────────────────────────────

  @Post('auth/register')
  @ApiOperation({ summary: 'Register a new user account with track selection (padel / pilates)' })
  @ApiCreatedResponse({ description: 'Account created; verification email queued' })
  @ApiConflictResponse({ description: 'Email already in use' })
  async register(@Body() dto: RegisterDto): Promise<{ userId: string }> {
    return this.identity.register(dto);
  }

  // ── Login ─────────────────────────────────────────────────

  @Post('auth/login')
  @UseGuards(LocalAuthGuard)
  @HttpCode(HttpStatus.OK)
  @Throttle({ short: { limit: 5, ttl: 60_000 } })
  @ApiOperation({ summary: 'Authenticate with email + password; returns access + refresh tokens' })
  @ApiOkResponse({ description: 'Tokens issued; Next.js BFF writes them as HttpOnly cookies' })
  @ApiUnauthorizedResponse({ description: 'Invalid credentials' })
  async login(
    @Req() req: FastifyRequest,
    @Res({ passthrough: true }) _res: FastifyReply,
  ): Promise<{ accessToken: string; refreshToken: string }> {
    const user = req['user'] as AuthenticatedUser;
    return this.identity.issueTokens(user);
  }

  // ── Token refresh ─────────────────────────────────────────

  @Post('auth/refresh')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Rotate refresh token and issue a new access token' })
  @ApiUnauthorizedResponse({ description: 'Invalid or expired refresh token' })
  async refresh(
    @Body('refreshToken') refreshToken: string,
  ): Promise<{ accessToken: string; refreshToken: string }> {
    return this.identity.rotateRefreshToken(refreshToken);
  }

  // ── Logout ────────────────────────────────────────────────

  @Post('auth/logout')
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiBearerAuth('access-token')
  @ApiOperation({ summary: 'Revoke all refresh tokens for the current user (all devices)' })
  async logout(@CurrentUser() user: AuthenticatedUser): Promise<void> {
    return this.identity.revokeAllSessions(user.id);
  }

  // ── Profile ───────────────────────────────────────────────

  @Get('me')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth('access-token')
  @ApiOperation({ summary: 'Return full profile: name, email, tracks, wallet balance, pack counts' })
  @ApiOkResponse({ description: 'User profile object' })
  async getMe(@CurrentUser() user: AuthenticatedUser): Promise<Record<string, unknown>> {
    return this.identity.getMe(user.id);
  }

  @Patch('me')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth('access-token')
  @ApiOperation({ summary: 'Update mutable profile fields: name, phone, tracks, locale' })
  async updateMe(
    @CurrentUser() user: AuthenticatedUser,
    @Body() dto: UpdateMeDto,
  ): Promise<Record<string, unknown>> {
    return this.identity.updateMe(user.id, dto);
  }

  @Post('me/level-survey')
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.OK)
  @ApiBearerAuth('access-token')
  @ApiOperation({ summary: 'Submit padel level self-assessment survey to seed ELO starting points' })
  async levelSurvey(
    @CurrentUser() user: AuthenticatedUser,
    @Body() dto: LevelSurveyDto,
  ): Promise<{ newLevel: number; points: number }> {
    return this.identity.submitLevelSurvey(user.id, dto);
  }
}

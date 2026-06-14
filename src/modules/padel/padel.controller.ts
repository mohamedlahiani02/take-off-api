import {
  Controller,
  Get,
  Post,
  Body,
  Param,
  Query,
  UseGuards,
  HttpCode,
  HttpStatus,
  Headers,
  ParseUUIDPipe,
  ParseIntPipe,
  DefaultValuePipe,
} from '@nestjs/common';
import {
  ApiTags,
  ApiOperation,
  ApiBearerAuth,
  ApiOkResponse,
  ApiCreatedResponse,
  ApiQuery,
} from '@nestjs/swagger';
import { PadelService } from './padel.service';
import { JwtAuthGuard } from '../identity/auth.guard';
import { CurrentUser, AuthenticatedUser } from '../../common/current-user.decorator';
import { CreateBookingDto, CancelBookingDto } from './dto/create-booking.dto';
import { TournamentRegisterDto } from './dto/tournament-register.dto';

@ApiTags('Padel')
@Controller('padel')
export class PadelController {
  constructor(private readonly padel: PadelService) {}

  // ── Slots (public) ────────────────────────────────────────

  @Get('slots')
  @ApiOperation({ summary: 'Return 15-day slot calendar for all courts (public, ETag-cached)' })
  @ApiQuery({ name: 'from', required: false, example: '2026-06-14', description: 'Start date YYYY-MM-DD (default: today)' })
  @ApiQuery({ name: 'days', required: false, example: 15, description: 'Number of days (max 15)' })
  @ApiOkResponse({ description: 'Array of slots with share availability and court info' })
  async getSlots(
    @Query('from') from = new Date().toISOString().slice(0, 10),
    @Query('days', new DefaultValuePipe(15), ParseIntPipe) days: number,
  ): Promise<unknown> {
    return this.padel.getSlots(from, days);
  }

  // ── Bookings (auth required) ──────────────────────────────

  @Post('bookings')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth('access-token')
  @ApiOperation({ summary: 'Book a padel slot (SHARE or FULL_COURT). Requires Idempotency-Key header.' })
  @ApiCreatedResponse({ description: 'Booking created; status HELD (card) or CONFIRMED (wallet/pack)' })
  async createBooking(
    @CurrentUser() user: AuthenticatedUser,
    @Body() dto: CreateBookingDto,
    @Headers('idempotency-key') idempotencyKey: string,
  ): Promise<unknown> {
    return this.padel.createBooking(user.id, dto, idempotencyKey);
  }

  @Post('bookings/:id/cancel')
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.OK)
  @ApiBearerAuth('access-token')
  @ApiOperation({ summary: 'Cancel a booking. ≥4h before → full refund to wallet; <4h → 50% refund.' })
  async cancelBooking(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) bookingId: string,
    @Body() _dto: CancelBookingDto,
  ): Promise<unknown> {
    return this.padel.cancelBooking(user.id, bookingId);
  }

  // ── Packs ─────────────────────────────────────────────────

  @Get('packs')
  @ApiOperation({ summary: 'Return the padel pack catalog (Drop-in, 10-pack, 25-pack, 50-pack)' })
  @ApiOkResponse({ description: 'Array of Pack objects with price (millimes), matches, validity' })
  async getPackCatalog(): Promise<unknown> {
    return this.padel.getPackCatalog();
  }

  @Post('packs/:slug/purchase')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth('access-token')
  @ApiOperation({ summary: 'Begin checkout to purchase a pack by slug. Returns payment intent.' })
  async purchasePack(
    @CurrentUser() user: AuthenticatedUser,
    @Param('slug') slug: string,
    @Body('paymentSource') paymentSource: string,
    @Headers('idempotency-key') _idempotencyKey: string,
  ): Promise<unknown> {
    return this.padel.purchasePack(user.id, slug, paymentSource);
  }

  // ── Tournaments ───────────────────────────────────────────

  @Get('tournaments')
  @ApiOperation({ summary: 'List upcoming tournaments (Americano / Knockout format)' })
  async getTournaments(): Promise<unknown> {
    return this.padel.getTournaments();
  }

  @Post('tournaments/:id/register')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth('access-token')
  @ApiOperation({ summary: 'Register for a tournament. Paid registration (WALLET or CARD).' })
  async registerForTournament(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) tournamentId: string,
    @Body() dto: TournamentRegisterDto,
    @Headers('idempotency-key') _idempotencyKey: string,
  ): Promise<unknown> {
    return this.padel.registerForTournament(user.id, tournamentId, dto);
  }
}

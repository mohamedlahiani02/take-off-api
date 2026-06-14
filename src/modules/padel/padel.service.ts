import { Injectable, Logger } from '@nestjs/common';
import { DrizzleService } from '../../infrastructure/drizzle.service';
import { CreateBookingDto } from './dto/create-booking.dto';
import { TournamentRegisterDto } from './dto/tournament-register.dto';

/**
 * PadelService — business logic for courts, slots, bookings, packs, tournaments.
 *
 * Critical path: bookSlot() must implement optimistic locking (version column)
 * and idempotency-key caching. See ARCHITECTURE.md §11.1 for the full pseudocode.
 *
 * Money is stored as bigint millimes (1 DT = 1000 millimes).
 * Share price: 20_000 millimes. Full court: 80_000 millimes.
 */
@Injectable()
export class PadelService {
  private readonly logger = new Logger(PadelService.name);

  constructor(private readonly drizzle: DrizzleService) {}

  /**
   * Return the 15-day slot calendar for one or all courts.
   * Public endpoint — no auth required.
   * TODO: SELECT from padel.slot JOIN padel.court WHERE date_local BETWEEN from AND from+days
   */
  async getSlots(_from: string, _days: number): Promise<unknown[]> {
    // TODO: implement
    this.logger.debug({ from: _from, days: _days }, 'getSlots (TODO)');
    return [];
  }

  /**
   * Book a padel slot.
   * Implements: optimistic lock (version), pack consumption FIFO, wallet debit.
   * See ARCHITECTURE.md §11.1 for the full transactional pseudocode.
   * TODO: implement
   */
  async createBooking(
    _userId: string,
    _dto: CreateBookingDto,
    _idempotencyKey: string,
  ): Promise<unknown> {
    throw new Error('PadelService.createBooking not implemented — TODO');
  }

  /**
   * Cancel a booking.
   * Refund rules: ≥4h before start → full wallet refund, <4h → 50%.
   * TODO: implement
   */
  async cancelBooking(_userId: string, _bookingId: string): Promise<unknown> {
    throw new Error('PadelService.cancelBooking not implemented — TODO');
  }

  /** Return pack catalog. TODO */
  async getPackCatalog(): Promise<unknown[]> {
    return [];
  }

  /** Purchase a pack by slug. TODO */
  async purchasePack(_userId: string, _slug: string, _paymentSource: string): Promise<unknown> {
    throw new Error('PadelService.purchasePack not implemented — TODO');
  }

  /** Return user's active pack instances. TODO */
  async getUserPacks(_userId: string): Promise<unknown[]> {
    return [];
  }

  /** Return tournament list. TODO */
  async getTournaments(): Promise<unknown[]> {
    return [];
  }

  /** Register user for a tournament. TODO */
  async registerForTournament(
    _userId: string,
    _tournamentId: string,
    _dto: TournamentRegisterDto,
  ): Promise<unknown> {
    throw new Error('PadelService.registerForTournament not implemented — TODO');
  }
}

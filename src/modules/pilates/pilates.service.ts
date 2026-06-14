import { Injectable, Logger } from '@nestjs/common';
import { DrizzleService } from '../../infrastructure/drizzle.service';
import { CreateReservationDto } from './dto/create-reservation.dto';

/**
 * PilatesService — business logic for class catalog, sessions schedule, reservations.
 *
 * Reservations follow the same concurrency pattern as padel bookings:
 * spotsTaken column + optimistic lock to prevent over-booking.
 * Waitlist logic: when spotsTaken == capacity, status → WAITLISTED.
 *
 * See ARCHITECTURE.md §3.3 and domain model §8 (ClassSession, Reservation).
 */
@Injectable()
export class PilatesService {
  private readonly logger = new Logger(PilatesService.name);

  constructor(private readonly drizzle: DrizzleService) {}

  /** List class definitions (Reformer, Mat, Sculpt, Prenatal, Private). TODO */
  async getClassDefinitions(): Promise<unknown[]> {
    return [];
  }

  /** Return 7-day rolling session schedule with live spots-left. TODO */
  async getSessions(_from: string, _days: number): Promise<unknown[]> {
    this.logger.debug({ from: _from, days: _days }, 'getSessions (TODO)');
    return [];
  }

  /** Reserve a spot in a class session. Waitlist if full. TODO */
  async createReservation(
    _userId: string,
    _dto: CreateReservationDto,
    _idempotencyKey: string,
  ): Promise<unknown> {
    throw new Error('PilatesService.createReservation not implemented — TODO');
  }

  /** Cancel a reservation and shift waitlist. TODO */
  async cancelReservation(_userId: string, _reservationId: string): Promise<unknown> {
    throw new Error('PilatesService.cancelReservation not implemented — TODO');
  }
}

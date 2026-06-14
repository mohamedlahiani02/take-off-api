import { Injectable, Logger } from '@nestjs/common';
import { DrizzleService } from '../../infrastructure/drizzle.service';
import { CreateInquiryDto } from './dto/create-inquiry.dto';

/**
 * CoachingService — coach roster and coaching inquiry lead capture.
 *
 * Inquiries are anonymous-allowed but rate-limited:
 *   3 per day per email (Redis token bucket by email)
 *   IP-level throttling via @nestjs/throttler
 *
 * Captcha is required when not authenticated.
 * Status workflow: NEW → CONTACTED → CLOSED | WON
 *
 * See ARCHITECTURE.md §11.5 (Coaching inquiry) and §3.2 (Coaches).
 */
@Injectable()
export class CoachingService {
  private readonly logger = new Logger(CoachingService.name);

  constructor(private readonly drizzle: DrizzleService) {}

  /** Return public coach list, optionally filtered by sport. TODO */
  async getCoaches(_sport?: string): Promise<unknown[]> {
    this.logger.debug({ sport: _sport }, 'getCoaches (TODO)');
    return [];
  }

  /** Return a coach profile by slug. TODO */
  async getCoachBySlug(_slug: string): Promise<unknown> {
    throw new Error('CoachingService.getCoachBySlug not implemented — TODO');
  }

  /**
   * Create a coaching inquiry lead.
   * - Verify captcha if anonymous.
   * - Rate-limit 3/day/email.
   * - Insert into coaching.coaching_inquiry.
   * - Fire InquiryReceived outbox event → email notification to ops team.
   * TODO: implement
   */
  async createInquiry(
    _dto: CreateInquiryDto,
    _userId?: string,
  ): Promise<{ inquiryId: string }> {
    throw new Error('CoachingService.createInquiry not implemented — TODO');
  }
}

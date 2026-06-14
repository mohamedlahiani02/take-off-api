import { Injectable, Logger, OnModuleInit, OnModuleDestroy } from '@nestjs/common';
import { DrizzleService } from '../../infrastructure/drizzle.service';
import { ResendClient } from '../../infrastructure/resend.client';

/**
 * NotificationsWorker — polls outbox_event table and dispatches notifications.
 *
 * Poll interval: 5 seconds.
 * Retry: exponential backoff, max 5 attempts → dead-letter (attempts column).
 * Event types handled (TODO — add handlers as features are implemented):
 *   - UserRegistered        → send verification email
 *   - BookingConfirmed      → send booking confirmation email + WhatsApp
 *   - OrderConfirmed        → send order confirmation email
 *   - InquiryReceived       → notify ops team email
 *   - PaymentFailed         → notify user
 *
 * See ARCHITECTURE.md §14 (Notifications) and §7 (Outbox pattern).
 */
@Injectable()
export class NotificationsWorker implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(NotificationsWorker.name);
  private intervalHandle?: NodeJS.Timeout;

  constructor(
    private readonly drizzle: DrizzleService,
    private readonly resend: ResendClient,
  ) {}

  onModuleInit(): void {
    this.intervalHandle = setInterval(() => {
      void this.poll();
    }, 5_000);
    this.logger.log('Notifications worker started (poll every 5s)');
  }

  onModuleDestroy(): void {
    if (this.intervalHandle) {
      clearInterval(this.intervalHandle);
    }
  }

  private async poll(): Promise<void> {
    // TODO: SELECT * FROM outbox.outbox_event
    //         WHERE published_at IS NULL AND attempts < 5
    //         ORDER BY created_at ASC LIMIT 10
    //         FOR UPDATE SKIP LOCKED
    // TODO: for each event, dispatch to the right handler
    // TODO: on success: UPDATE SET published_at = NOW()
    // TODO: on failure: UPDATE SET attempts = attempts + 1
    this.logger.verbose('Outbox poll (TODO — no events processed yet)');
    void this.drizzle;
    void this.resend;
  }

  /**
   * Handle a UserRegistered event.
   * TODO: send verification email via ResendClient.
   */
  private async handleUserRegistered(_payload: Record<string, unknown>): Promise<void> {
    // TODO
  }

  /**
   * Handle a BookingConfirmed event.
   * TODO: send booking confirmation email.
   */
  private async handleBookingConfirmed(_payload: Record<string, unknown>): Promise<void> {
    // TODO
  }
}

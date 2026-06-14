import { Injectable, Logger } from '@nestjs/common';
import { DrizzleService } from '../../infrastructure/drizzle.service';
import { KonnectClient } from '../../infrastructure/konnect.client';

/**
 * PaymentsService — payment intent lifecycle and webhook reconciliation.
 *
 * Abstracted behind KonnectClient; swapping the provider only requires
 * a new adapter implementing the same interface.
 *
 * See ARCHITECTURE.md §13 (Payments — provider abstraction).
 */
@Injectable()
export class PaymentsService {
  private readonly logger = new Logger(PaymentsService.name);

  constructor(
    private readonly drizzle: DrizzleService,
    private readonly konnect: KonnectClient,
  ) {}

  /**
   * Handle inbound payment webhook from Konnect.
   * 1. Verify HMAC signature + timestamp skew (≤5 min).
   * 2. Parse payload → find matching payment_intent row.
   * 3. Dispatch to the originating module (booking / order / pack / wallet).
   * 4. Mark intent as COMPLETED / FAILED.
   * TODO: implement
   */
  async handleWebhook(_provider: string, _rawBody: string, _signature: string, _timestamp: string): Promise<void> {
    // TODO: verify signature via konnect.verifyWebhookSignature
    // TODO: konnect.parseWebhook → find order/booking by providerRef
    // TODO: transition order/booking status, fire outbox event
    this.logger.log({ provider: _provider }, 'Webhook received (TODO)');
  }

  /**
   * Reconciliation cron — called every 15 min.
   * Queries Konnect for any pending intents older than 5 min that haven't received a webhook.
   * TODO: implement
   */
  async reconcilePendingIntents(): Promise<void> {
    // TODO: SELECT * FROM payments.payment_intent WHERE status = 'PENDING' AND created_at < NOW() - INTERVAL '5 minutes'
    // TODO: for each, call konnect.getPaymentStatus and update accordingly
    this.logger.debug('reconcilePendingIntents (TODO)');
  }
}

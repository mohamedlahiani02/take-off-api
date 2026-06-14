import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

export type PaymentIntentStatus =
  | 'PENDING'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'FAILED';

export interface CreatePaymentIntentParams {
  /** Amount in millimes (1 DT = 1000 millimes) */
  amountMillimes: bigint;
  orderId: string;
  description: string;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  /** Redirect URL after payment on provider's surface */
  successUrl: string;
  failureUrl: string;
  webhookUrl: string;
}

export interface PaymentIntentResult {
  providerRef: string;
  redirectUrl: string;
}

export interface WebhookPayload {
  orderId: string;
  providerRef: string;
  status: PaymentIntentStatus;
  amountMillimes: bigint;
  rawPayload: Record<string, unknown>;
}

/**
 * KonnectClient — adapter for the Konnect (Tunisia) payment gateway.
 *
 * See ARCHITECTURE.md §13 (Payments — Konnect adapter).
 * All methods are TODO placeholders; implement when Konnect merchant
 * account is active (see BOOTSTRAP.md Step 9 / INFRASTRUCTURE.md §16).
 *
 * Flow:
 *   1. backend calls createPaymentIntent → gets redirectUrl
 *   2. user completes payment on Konnect's surface
 *   3. Konnect POSTs signed webhook → PaymentsController.handleWebhook
 *   4. reconcileIntent is called as a fallback every 15 min
 *
 * HMAC signature uses the KONNECT_WEBHOOK_SECRET env var.
 */
@Injectable()
export class KonnectClient implements OnModuleInit {
  private readonly logger = new Logger(KonnectClient.name);
  private apiKey!: string;
  private apiUrl!: string;
  private walletId!: string;

  constructor(private readonly config: ConfigService) {}

  onModuleInit(): void {
    this.apiKey = this.config.get<string>('KONNECT_API_KEY', '');
    this.apiUrl = this.config.get<string>('KONNECT_API_URL', 'https://api.konnect.network');
    this.walletId = this.config.get<string>('KONNECT_WALLET_ID', '');

    if (!this.apiKey) {
      this.logger.warn('KONNECT_API_KEY not set — payments will fail at runtime');
    }
  }

  /**
   * Create a payment intent on Konnect.
   * TODO: POST to /api/payments/initPayment with amount, orderId, webhookUrl, etc.
   */
  async createPaymentIntent(
    _params: CreatePaymentIntentParams,
  ): Promise<PaymentIntentResult> {
    // TODO: implement real Konnect API call
    throw new Error('KonnectClient.createPaymentIntent not implemented');
  }

  /**
   * Retrieve the current status of a payment intent from Konnect.
   * Used by the 15-min reconciliation cron to finalize stuck intents.
   * TODO: GET /api/payments/{providerRef}
   */
  async getPaymentStatus(_providerRef: string): Promise<PaymentIntentStatus> {
    // TODO: implement
    throw new Error('KonnectClient.getPaymentStatus not implemented');
  }

  /**
   * Verify the HMAC signature on an inbound Konnect webhook.
   * Reject if timestamp skew > 5 min.
   * TODO: use KONNECT_WEBHOOK_SECRET env var + crypto.createHmac('sha256', ...)
   */
  verifyWebhookSignature(_rawBody: string, _signature: string, _timestamp: string): boolean {
    // TODO: implement HMAC verification
    this.logger.warn('Webhook signature verification NOT implemented — accept all (dev only)');
    return true;
  }

  /**
   * Parse a raw webhook payload into our internal WebhookPayload shape.
   * TODO: map Konnect's JSON schema to WebhookPayload.
   */
  parseWebhook(_rawBody: string): WebhookPayload {
    // TODO: implement
    throw new Error('KonnectClient.parseWebhook not implemented');
  }
}

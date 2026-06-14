import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Resend } from 'resend';

export interface SendEmailOptions {
  to: string | string[];
  subject: string;
  html: string;
  from?: string;
  replyTo?: string;
}

/**
 * ResendClient — wrapper around the Resend transactional email SDK.
 *
 * Triggered from outbox event consumers (never from the request thread).
 * See ARCHITECTURE.md §14 (Notifications — Email / Resend).
 *
 * Free tier: 100 emails/day, 3K/month.
 */
@Injectable()
export class ResendClient implements OnModuleInit {
  private readonly logger = new Logger(ResendClient.name);
  private resend!: Resend;
  private fromEmail!: string;

  constructor(private readonly config: ConfigService) {}

  onModuleInit(): void {
    const apiKey = this.config.getOrThrow<string>('RESEND_API_KEY');
    this.fromEmail = this.config.get<string>('RESEND_FROM_EMAIL', 'noreply@takeoff.tn');
    this.resend = new Resend(apiKey);
    this.logger.log('Resend client initialised');
  }

  async send(options: SendEmailOptions): Promise<string> {
    // TODO: implement retry with exponential backoff (outbox handles dead-lettering)
    const { data, error } = await this.resend.emails.send({
      from: options.from ?? this.fromEmail,
      to: Array.isArray(options.to) ? options.to : [options.to],
      subject: options.subject,
      html: options.html,
      reply_to: options.replyTo,
    });

    if (error) {
      this.logger.error({ error }, 'Resend send failed');
      throw new Error(`Resend error: ${JSON.stringify(error)}`);
    }

    return data?.id ?? '';
  }
}

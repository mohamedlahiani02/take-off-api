import { Module } from '@nestjs/common';
import { NotificationsWorker } from './notifications.worker';
import { ResendClient } from '../../infrastructure/resend.client';

/**
 * NotificationsModule — outbox event consumer worker.
 *
 * The worker polls the outbox_event table every 5 seconds for unpublished events
 * and dispatches them to the appropriate channel (email, SMS, WhatsApp).
 *
 * Pattern: Transactional Outbox — see ARCHITECTURE.md §7 (Backend cross-cuts).
 * Never send notifications directly from the request thread.
 */
@Module({
  providers: [NotificationsWorker, ResendClient],
})
export class NotificationsModule {}

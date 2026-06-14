import { Injectable, Logger } from '@nestjs/common';
import { DrizzleService } from '../../infrastructure/drizzle.service';

/**
 * ContentService — CMS-ish static content backed by DB rows.
 * Partners, FAQ, and opening hours are stored in the content schema and
 * served with long-lived ETags (content rarely changes).
 *
 * See ARCHITECTURE.md §3.6 (Mega-menu, gateway, content).
 */
@Injectable()
export class ContentService {
  private readonly logger = new Logger(ContentService.name);

  constructor(private readonly drizzle: DrizzleService) {}

  /** Return active partner logos and links. TODO */
  async getPartners(): Promise<unknown[]> {
    this.logger.debug('getPartners (TODO)');
    return [];
  }

  /** Return FAQ items ordered by display_order. TODO */
  async getFaq(): Promise<unknown[]> {
    return [];
  }

  /** Return opening hours per day-of-week for padel + pilates. TODO */
  async getHours(): Promise<unknown> {
    return {};
  }
}

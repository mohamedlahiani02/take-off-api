import { Injectable, Logger } from '@nestjs/common';
import { DrizzleService } from '../../infrastructure/drizzle.service';
import { ProductQueryDto } from './dto/product-query.dto';

/**
 * CatalogService — product listing and detail.
 *
 * Search uses pg_trgm until traffic justifies a dedicated search index.
 * Prices stored as bigint millimes.
 * See ARCHITECTURE.md §3.4 (Store) and domain model §8 (Product, ProductVariant).
 */
@Injectable()
export class CatalogService {
  private readonly logger = new Logger(CatalogService.name);

  constructor(private readonly drizzle: DrizzleService) {}

  /** Paginated product list with optional category filter and full-text search. TODO */
  async getProducts(_query: ProductQueryDto): Promise<{ items: unknown[]; nextCursor: string | null }> {
    this.logger.debug({ query: _query }, 'getProducts (TODO)');
    return { items: [], nextCursor: null };
  }

  /** Product detail including all variants with stock. TODO */
  async getProductBySlug(_slug: string): Promise<unknown> {
    throw new Error('CatalogService.getProductBySlug not implemented — TODO');
  }
}

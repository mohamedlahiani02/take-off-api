import { Injectable, OnModuleDestroy, OnModuleInit, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { drizzle, NodePgDatabase } from 'drizzle-orm/node-postgres';
import { Pool } from 'pg';
import * as schema from '../../drizzle/schema/index';

export type DrizzleDb = NodePgDatabase<typeof schema>;

/**
 * DrizzleService wraps the pg Pool + drizzle-orm client.
 * Inject this service to get a typed `db` instance.
 *
 * All modules should destructure `db` from this service rather than
 * injecting the pool directly.
 */
@Injectable()
export class DrizzleService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(DrizzleService.name);
  private pool!: Pool;
  public db!: DrizzleDb;

  constructor(private readonly config: ConfigService) {}

  onModuleInit(): void {
    const connectionString = this.config.getOrThrow<string>('DATABASE_URL');

    this.pool = new Pool({
      connectionString,
      max: 20,
      idleTimeoutMillis: 30_000,
      connectionTimeoutMillis: 5_000,
    });

    this.db = drizzle(this.pool, { schema, logger: this.config.get('NODE_ENV') !== 'production' });
    this.logger.log('Drizzle connected to Postgres');
  }

  async onModuleDestroy(): Promise<void> {
    await this.pool.end();
    this.logger.log('Drizzle pool closed');
  }
}

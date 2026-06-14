/**
 * Drizzle schema barrel — exports all tables for use in drizzle() client.
 *
 * Import pattern in DrizzleService:
 *   import * as schema from '../../drizzle/schema/index';
 *   const db = drizzle(pool, { schema });
 */

export * from './identity';
export * from './padel';
export * from './pilates';
export * from './catalog';
export * from './orders';
export * from './wallet';
export * from './ranking';
export * from './coaching';
export * from './content';
export * from './payments';
export * from './outbox';

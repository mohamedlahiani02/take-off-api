import type { Config } from 'drizzle-kit';

const config: Config = {
  schema: './drizzle/schema/index.ts',
  out: './drizzle/migrations',
  dialect: 'postgresql',
  dbCredentials: {
    url: process.env['DATABASE_URL'] ?? 'postgres://takeoff:takeoff@localhost:5432/takeoff',
  },
  verbose: true,
  strict: true,
};

export default config;

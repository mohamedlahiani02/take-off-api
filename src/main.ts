import 'reflect-metadata';
import { NestFactory } from '@nestjs/core';
import {
  FastifyAdapter,
  NestFastifyApplication,
} from '@nestjs/platform-fastify';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import { ValidationPipe, VersioningType } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Logger } from 'nestjs-pino';
import * as Sentry from '@sentry/node';
import { AppModule } from './app.module';
import { ProblemDetailsFilter } from './common/problem-details.filter';

async function bootstrap(): Promise<void> {
  // ── Fastify adapter ──────────────────────────────────────
  const app = await NestFactory.create<NestFastifyApplication>(
    AppModule,
    new FastifyAdapter({ logger: false }),
    { bufferLogs: true },
  );

  const configService = app.get(ConfigService);
  const port = configService.get<number>('PORT', 3001);
  const nodeEnv = configService.get<string>('NODE_ENV', 'development');
  const corsOrigins = configService.get<string>('CORS_ORIGINS', 'http://localhost:3000');
  const apiPrefix = configService.get<string>('API_PREFIX', '/v1');

  // ── Pino logger ───────────────────────────────────────────
  app.useLogger(app.get(Logger));

  // ── Sentry (conditional on DSN) ──────────────────────────
  const sentryDsn = configService.get<string>('SENTRY_DSN');
  if (sentryDsn) {
    Sentry.init({
      dsn: sentryDsn,
      environment: configService.get<string>('SENTRY_ENVIRONMENT', nodeEnv),
      tracesSampleRate: configService.get<number>('SENTRY_TRACES_SAMPLE_RATE', 0.1),
    });
  }

  // ── Fastify plugins ───────────────────────────────────────
  await app.register(
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    require('@fastify/helmet') as Parameters<typeof app.register>[0],
    {
      contentSecurityPolicy: {
        directives: {
          defaultSrc: ["'self'"],
          styleSrc: ["'self'", "'unsafe-inline'"],
          imgSrc: ["'self'", 'data:', 'https:'],
          scriptSrc: ["'self'"],
        },
      },
    },
  );

  await app.register(
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    require('@fastify/cors') as Parameters<typeof app.register>[0],
    {
      origin: corsOrigins.split(',').map((o) => o.trim()),
      credentials: true,
      methods: ['GET', 'HEAD', 'PUT', 'PATCH', 'POST', 'DELETE', 'OPTIONS'],
    },
  );

  await app.register(
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    require('@fastify/cookie') as Parameters<typeof app.register>[0],
  );

  // ── Global prefix & versioning ───────────────────────────
  app.setGlobalPrefix(apiPrefix.replace(/^\//, ''));

  // ── Global pipes & filters ───────────────────────────────
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
      transformOptions: { enableImplicitConversion: false },
    }),
  );
  app.useGlobalFilters(new ProblemDetailsFilter());

  // ── Swagger / OpenAPI ─────────────────────────────────────
  if (nodeEnv !== 'production') {
    const swaggerConfig = new DocumentBuilder()
      .setTitle('Take Off API')
      .setDescription(
        'REST API for Take Off club — Padel · Pilates · Store · Wallet · Ranking',
      )
      .setVersion('1.0')
      .addBearerAuth({ type: 'http', scheme: 'bearer', bearerFormat: 'JWT' }, 'access-token')
      .addCookieAuth('access_token')
      .addServer(`http://localhost:${port}`, 'Local')
      .addServer('https://staging.api.takeoff.tn', 'Staging')
      .addServer('https://api.takeoff.tn', 'Production')
      .build();

    const document = SwaggerModule.createDocument(app, swaggerConfig);
    SwaggerModule.setup('docs', app, document, {
      swaggerOptions: { persistAuthorization: true },
    });
  }

  // ── Health endpoint (outside the v1 prefix) ───────────────
  // Registered directly by HealthModule — see modules below

  await app.listen(port, '0.0.0.0');
  const logger = app.get(Logger);
  logger.log(`API listening on port ${port}`, 'Bootstrap');
  logger.log(`OpenAPI docs at http://localhost:${port}/docs`, 'Bootstrap');
}

void bootstrap();

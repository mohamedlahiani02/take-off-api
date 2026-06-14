import { Module, Global } from '@nestjs/common';
import { DrizzleService } from './drizzle.service';

/**
 * DrizzleModule — global singleton that provides the drizzle-orm client
 * backed by a pg Pool. Import into AppModule and use DrizzleService
 * everywhere else.
 */
@Global()
@Module({
  providers: [DrizzleService],
  exports: [DrizzleService],
})
export class DrizzleModule {}

import { Module } from '@nestjs/common';
import { PaymentsController } from './payments.controller';
import { PaymentsService } from './payments.service';
import { KonnectClient } from '../../infrastructure/konnect.client';

@Module({
  controllers: [PaymentsController],
  providers: [PaymentsService, KonnectClient],
  exports: [PaymentsService],
})
export class PaymentsModule {}

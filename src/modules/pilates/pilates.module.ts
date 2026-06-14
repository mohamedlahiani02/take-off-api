import { Module } from '@nestjs/common';
import { PilatesController } from './pilates.controller';
import { PilatesService } from './pilates.service';

@Module({
  controllers: [PilatesController],
  providers: [PilatesService],
})
export class PilatesModule {}

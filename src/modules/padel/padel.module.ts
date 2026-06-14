import { Module } from '@nestjs/common';
import { PadelController } from './padel.controller';
import { PadelService } from './padel.service';

@Module({
  controllers: [PadelController],
  providers: [PadelService],
  exports: [PadelService],
})
export class PadelModule {}

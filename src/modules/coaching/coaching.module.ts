import { Module } from '@nestjs/common';
import { CoachingController } from './coaching.controller';
import { CoachingService } from './coaching.service';

@Module({
  controllers: [CoachingController],
  providers: [CoachingService],
})
export class CoachingModule {}

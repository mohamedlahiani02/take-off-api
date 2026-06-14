import { IsIn } from 'class-validator';
import { ApiProperty } from '@nestjs/swagger';

export class TournamentRegisterDto {
  @ApiProperty({ enum: ['WALLET', 'CARD'], description: 'Payment source for tournament registration fee' })
  @IsIn(['WALLET', 'CARD'])
  paymentSource!: 'WALLET' | 'CARD';
}

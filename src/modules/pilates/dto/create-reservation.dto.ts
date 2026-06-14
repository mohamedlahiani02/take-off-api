import { IsUUID, IsIn } from 'class-validator';
import { ApiProperty } from '@nestjs/swagger';

export class CreateReservationDto {
  @ApiProperty({ description: 'ID of the class session to reserve a spot in', format: 'uuid' })
  @IsUUID()
  sessionId!: string;

  @ApiProperty({ enum: ['WALLET', 'CARD'], description: 'Payment source for this reservation' })
  @IsIn(['WALLET', 'CARD'])
  paymentSource!: 'WALLET' | 'CARD';
}

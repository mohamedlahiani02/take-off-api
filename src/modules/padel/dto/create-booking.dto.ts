import { IsUUID, IsIn, IsString } from 'class-validator';
import { ApiProperty } from '@nestjs/swagger';

export class CreateBookingDto {
  @ApiProperty({ description: 'ID of the padel slot to book', format: 'uuid' })
  @IsUUID()
  slotId!: string;

  @ApiProperty({
    description: 'SHARE books one of four share positions (20 DT). FULL_COURT rents all 4 (80 DT)',
    enum: ['SHARE', 'FULL_COURT'],
  })
  @IsIn(['SHARE', 'FULL_COURT'])
  type!: 'SHARE' | 'FULL_COURT';

  @ApiProperty({
    description: 'Payment source. CARD creates a PaymentIntent and holds the slot.',
    enum: ['WALLET', 'PACK', 'CARD'],
  })
  @IsIn(['WALLET', 'PACK', 'CARD'])
  paymentSource!: 'WALLET' | 'PACK' | 'CARD';
}

export class CancelBookingDto {
  @ApiProperty({ description: 'Optional cancellation reason', required: false })
  @IsString()
  reason?: string;
}

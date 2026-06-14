import { IsInt, Min, IsIn } from 'class-validator';
import { ApiProperty } from '@nestjs/swagger';

export class TopUpDto {
  @ApiProperty({
    description: 'Amount to top up in millimes (1 DT = 1000 millimes). Minimum 10 DT.',
    example: 50000,
    minimum: 10000,
  })
  @IsInt()
  @Min(10_000)
  amountMillimes!: number;

  @ApiProperty({ enum: ['CARD'], description: 'Only CARD is supported for top-ups' })
  @IsIn(['CARD'])
  paymentSource!: 'CARD';
}

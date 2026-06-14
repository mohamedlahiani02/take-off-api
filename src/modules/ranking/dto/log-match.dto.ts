import { IsString, IsIn, IsOptional, IsInt, Min, Max } from 'class-validator';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

export class LogMatchDto {
  @ApiPropertyOptional({ description: 'Partner display name (optional for singles)' })
  @IsOptional()
  @IsString()
  partnerText?: string;

  @ApiProperty({ description: 'Opponent name(s) free text', example: 'Ahmed & Karim' })
  @IsString()
  opponentText!: string;

  @ApiProperty({ enum: ['W', 'L'], description: 'Match result: W = win, L = loss' })
  @IsIn(['W', 'L'])
  result!: 'W' | 'L';

  @ApiProperty({ description: 'Score string e.g. "6-4, 7-5"', example: '6-4, 7-5' })
  @IsString()
  score!: string;

  @ApiPropertyOptional({
    description: 'Estimated opponent level 1-7 (used for ELO delta multiplier)',
    minimum: 1,
    maximum: 7,
  })
  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(7)
  opponentLevelEst?: number;
}

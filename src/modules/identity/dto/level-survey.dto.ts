import { IsInt, Min, Max, IsString, IsOptional } from 'class-validator';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

export class LevelSurveyDto {
  @ApiProperty({
    description: 'Self-declared padel level 1-7',
    minimum: 1,
    maximum: 7,
    example: 3,
  })
  @IsInt()
  @Min(1)
  @Max(7)
  selfDeclaredLevel!: number;

  @ApiPropertyOptional({ example: 'Been playing for 2 years, casual weekends' })
  @IsOptional()
  @IsString()
  notes?: string;
}

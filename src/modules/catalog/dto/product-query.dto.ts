import { IsOptional, IsString, IsIn, IsInt, Min, Max } from 'class-validator';
import { Type } from 'class-transformer';
import { ApiPropertyOptional } from '@nestjs/swagger';

export class ProductQueryDto {
  @ApiPropertyOptional({
    description: 'Filter by product category',
    enum: ['rackets', 'accessories', 'padelwear', 'towels', 'pilates', 'lifestyle'],
  })
  @IsOptional()
  @IsIn(['rackets', 'accessories', 'padelwear', 'towels', 'pilates', 'lifestyle'])
  cat?: string;

  @ApiPropertyOptional({ description: 'Full-text search query (pg_trgm)', example: 'Head Alpha' })
  @IsOptional()
  @IsString()
  q?: string;

  @ApiPropertyOptional({ description: 'Cursor for pagination (last product ID seen)', format: 'uuid' })
  @IsOptional()
  @IsString()
  cursor?: string;

  @ApiPropertyOptional({ default: 20, minimum: 1, maximum: 50 })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(50)
  limit?: number = 20;
}

import {
  IsString,
  IsOptional,
  MinLength,
  MaxLength,
  IsArray,
  IsIn,
  IsPhoneNumber,
} from 'class-validator';
import { ApiPropertyOptional } from '@nestjs/swagger';

export class UpdateMeDto {
  @ApiPropertyOptional({ example: 'Alice Dupont' })
  @IsOptional()
  @IsString()
  @MinLength(2)
  @MaxLength(100)
  name?: string;

  @ApiPropertyOptional({ example: '+21627314100' })
  @IsOptional()
  @IsPhoneNumber()
  phone?: string;

  @ApiPropertyOptional({ enum: ['padel', 'pilates'], isArray: true })
  @IsOptional()
  @IsArray()
  @IsIn(['padel', 'pilates'], { each: true })
  tracks?: string[];

  @ApiPropertyOptional({ enum: ['fr', 'en', 'ar'], example: 'fr' })
  @IsOptional()
  @IsIn(['fr', 'en', 'ar'])
  locale?: string;
}

import {
  IsString,
  IsEmail,
  IsPhoneNumber,
  IsArray,
  IsIn,
  IsOptional,
  IsBoolean,
  ArrayMaxSize,
  MinLength,
  MaxLength,
} from 'class-validator';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

export class CreateInquiryDto {
  @ApiProperty({ example: 'Mohamed Lahiani' })
  @IsString()
  @MinLength(2)
  @MaxLength(100)
  name!: string;

  @ApiProperty({ example: '+21627314100' })
  @IsPhoneNumber()
  phone!: string;

  @ApiProperty({ example: 'client@example.com' })
  @IsEmail()
  email!: string;

  @ApiProperty({
    description: 'Types of coaching desired',
    enum: ['individual', 'group', 'kids'],
    isArray: true,
    example: ['individual'],
  })
  @IsArray()
  @IsIn(['individual', 'group', 'kids'], { each: true })
  courseTypes!: string[];

  @ApiPropertyOptional({ description: 'Note about group size or session goals' })
  @IsOptional()
  @IsString()
  @MaxLength(500)
  groupNote?: string;

  @ApiPropertyOptional({
    description: '7×4 availability matrix (7 days × 4 slots: morning/noon/afternoon/evening)',
    example: [[true, false, true, false]],
  })
  @IsOptional()
  @IsArray()
  @ArrayMaxSize(7)
  availabilityMatrix?: boolean[][];

  @ApiPropertyOptional({ description: 'Self-declared padel level 1-7', example: '3' })
  @IsOptional()
  @IsString()
  niveau?: string;

  @ApiPropertyOptional({ description: 'Other sports practiced' })
  @IsOptional()
  @IsString()
  otherSports?: string;

  @ApiPropertyOptional({ description: 'Motivation / goals free text' })
  @IsOptional()
  @IsString()
  @MaxLength(1000)
  motivation?: string;

  @ApiPropertyOptional({ enum: ['fr', 'en', 'ar'], default: 'fr' })
  @IsOptional()
  @IsIn(['fr', 'en', 'ar'])
  language?: string = 'fr';

  @ApiPropertyOptional({ description: 'Additional notes from the prospect' })
  @IsOptional()
  @IsString()
  @MaxLength(1000)
  notes?: string;

  /** reCAPTCHA token — required when request is anonymous */
  @ApiPropertyOptional({ description: 'Google reCAPTCHA v3 token (required if not authenticated)' })
  @IsOptional()
  @IsString()
  captchaToken?: string;
}

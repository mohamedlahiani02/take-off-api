import {
  IsEmail,
  IsString,
  MinLength,
  MaxLength,
  IsArray,
  ArrayMinSize,
  IsIn,
  IsPhoneNumber,
  IsOptional,
} from 'class-validator';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

export class RegisterDto {
  @ApiProperty({ example: 'alice@example.com' })
  @IsEmail()
  email!: string;

  @ApiProperty({ example: 'Alice Dupont' })
  @IsString()
  @MinLength(2)
  @MaxLength(100)
  name!: string;

  @ApiPropertyOptional({ example: '+21627314100' })
  @IsOptional()
  @IsPhoneNumber()
  phone?: string;

  @ApiProperty({ minLength: 8, example: 'Str0ng!Pass' })
  @IsString()
  @MinLength(8)
  @MaxLength(128)
  password!: string;

  @ApiProperty({
    description: 'Sport tracks the user is registering for',
    enum: ['padel', 'pilates'],
    isArray: true,
    example: ['padel'],
  })
  @IsArray()
  @ArrayMinSize(1)
  @IsIn(['padel', 'pilates'], { each: true })
  tracks!: string[];
}

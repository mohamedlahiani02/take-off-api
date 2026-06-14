import {
  IsString,
  IsEmail,
  IsPhoneNumber,
  IsArray,
  ValidateNested,
  IsUUID,
  IsInt,
  Min,
  IsIn,
  IsOptional,
} from 'class-validator';
import { Type } from 'class-transformer';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

export class ContactDto {
  @ApiProperty()
  @IsString()
  name!: string;

  @ApiProperty()
  @IsEmail()
  email!: string;

  @ApiProperty()
  @IsPhoneNumber()
  phone!: string;
}

export class DeliveryDto {
  @ApiProperty()
  @IsString()
  address!: string;

  @ApiProperty()
  @IsString()
  city!: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  postalCode?: string;

  @ApiPropertyOptional({ default: 'TN' })
  @IsOptional()
  @IsString()
  country?: string = 'TN';
}

export class OrderItemDto {
  @ApiProperty({ format: 'uuid', description: 'ProductVariant ID' })
  @IsUUID()
  variantId!: string;

  @ApiProperty({ minimum: 1 })
  @IsInt()
  @Min(1)
  quantity!: number;
}

export class CreateOrderDto {
  @ApiProperty({ type: ContactDto })
  @ValidateNested()
  @Type(() => ContactDto)
  contact!: ContactDto;

  @ApiProperty({ type: DeliveryDto })
  @ValidateNested()
  @Type(() => DeliveryDto)
  delivery!: DeliveryDto;

  @ApiProperty({ type: [OrderItemDto] })
  @IsArray()
  @ValidateNested({ each: true })
  @Type(() => OrderItemDto)
  items!: OrderItemDto[];

  @ApiProperty({ enum: ['WALLET', 'CARD', 'MIXED'] })
  @IsIn(['WALLET', 'CARD', 'MIXED'])
  paymentSource!: 'WALLET' | 'CARD' | 'MIXED';

  /** Amount in millimes to pay from wallet when paymentSource=MIXED */
  @ApiPropertyOptional({ description: 'Wallet portion in millimes for MIXED payment' })
  @IsOptional()
  @IsInt()
  @Min(0)
  walletAmountMillimes?: number;
}

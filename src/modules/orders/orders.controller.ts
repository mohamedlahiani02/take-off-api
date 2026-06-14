import {
  Controller,
  Get,
  Post,
  Body,
  Param,
  Query,
  UseGuards,
  Headers,
  ParseUUIDPipe,
} from '@nestjs/common';
import { ApiTags, ApiOperation, ApiBearerAuth, ApiCreatedResponse, ApiOkResponse } from '@nestjs/swagger';
import { OrdersService } from './orders.service';
import { JwtAuthGuard } from '../identity/auth.guard';
import { CurrentUser, AuthenticatedUser } from '../../common/current-user.decorator';
import { CreateOrderDto } from './dto/create-order.dto';

@ApiTags('Orders')
@Controller('orders')
@UseGuards(JwtAuthGuard)
@ApiBearerAuth('access-token')
export class OrdersController {
  constructor(private readonly orders: OrdersService) {}

  @Post()
  @ApiOperation({ summary: 'Submit full checkout payload; server re-validates prices and stock before charging' })
  @ApiCreatedResponse({ description: 'Order created. Returns orderId + paymentIntent if card payment.' })
  async createOrder(
    @CurrentUser() user: AuthenticatedUser,
    @Body() dto: CreateOrderDto,
    @Headers('idempotency-key') idempotencyKey: string,
  ): Promise<unknown> {
    return this.orders.createOrder(user.id, dto, idempotencyKey);
  }

  @Get()
  @ApiOperation({ summary: 'Return cursor-paginated order history for the current user' })
  @ApiOkResponse({ description: 'Array of Order objects with status and items' })
  async getOrders(
    @CurrentUser() user: AuthenticatedUser,
    @Query('cursor') cursor?: string,
  ): Promise<unknown> {
    return this.orders.getUserOrders(user.id, cursor);
  }

  @Get(':id')
  @ApiOperation({ summary: 'Return a single order detail including line items and fulfillment status' })
  async getOrder(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) orderId: string,
  ): Promise<unknown> {
    return this.orders.getOrder(user.id, orderId);
  }
}

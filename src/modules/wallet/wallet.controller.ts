import {
  Controller,
  Get,
  Post,
  Body,
  UseGuards,
  Headers,
} from '@nestjs/common';
import { ApiTags, ApiOperation, ApiBearerAuth, ApiCreatedResponse, ApiOkResponse } from '@nestjs/swagger';
import { WalletService } from './wallet.service';
import { JwtAuthGuard } from '../identity/auth.guard';
import { CurrentUser, AuthenticatedUser } from '../../common/current-user.decorator';
import { TopUpDto } from './dto/topup.dto';

@ApiTags('Wallet')
@Controller('wallet')
@UseGuards(JwtAuthGuard)
@ApiBearerAuth('access-token')
export class WalletController {
  constructor(private readonly wallet: WalletService) {}

  @Post('topup')
  @ApiOperation({ summary: 'Start a wallet top-up via card payment. Returns payment intent for redirect.' })
  @ApiCreatedResponse({ description: 'PaymentIntent created; redirect user to providerUrl' })
  async topUp(
    @CurrentUser() user: AuthenticatedUser,
    @Body() dto: TopUpDto,
    @Headers('idempotency-key') idempotencyKey: string,
  ): Promise<unknown> {
    return this.wallet.topUp(user.id, dto, idempotencyKey);
  }

  @Get('balance')
  @ApiOperation({ summary: 'Return wallet balance (sum of ledger) and paginated transaction history' })
  @ApiOkResponse({ description: 'balanceMillimes + entries[]' })
  async getBalance(@CurrentUser() user: AuthenticatedUser): Promise<unknown> {
    return this.wallet.getBalance(user.id);
  }
}

import {
  Controller,
  Post,
  Param,
  Req,
  Headers,
  HttpCode,
  HttpStatus,
  RawBodyRequest,
} from '@nestjs/common';
import { ApiTags, ApiOperation } from '@nestjs/swagger';
import { FastifyRequest } from 'fastify';
import { PaymentsService } from './payments.service';

@ApiTags('Payments')
@Controller('webhooks/payment')
export class PaymentsController {
  constructor(private readonly payments: PaymentsService) {}

  @Post(':provider')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary:
      'Receive a payment webhook from the specified provider (konnect, flouci). ' +
      'HMAC signature and timestamp skew (≤5 min) are verified before processing.',
  })
  async handleWebhook(
    @Param('provider') provider: string,
    @Req() req: RawBodyRequest<FastifyRequest>,
    @Headers('x-konnect-signature') signature: string,
    @Headers('x-konnect-timestamp') timestamp: string,
  ): Promise<{ received: boolean }> {
    const rawBody = req.rawBody?.toString('utf-8') ?? '';
    await this.payments.handleWebhook(provider, rawBody, signature, timestamp);
    return { received: true };
  }
}

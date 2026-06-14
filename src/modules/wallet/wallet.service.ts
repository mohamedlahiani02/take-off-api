import { Injectable, Logger } from '@nestjs/common';
import { DrizzleService } from '../../infrastructure/drizzle.service';
import { TopUpDto } from './dto/topup.dto';

/**
 * WalletService — immutable ledger operations.
 *
 * The wallet balance is DERIVED from the sum of wallet_entry.delta_tnd.
 * There is no mutable balance column — this is intentional for auditability.
 *
 * See ARCHITECTURE.md §8 (WalletEntry) and §3.5 (Wallet & payments).
 * All amounts in bigint millimes.
 */
@Injectable()
export class WalletService {
  private readonly logger = new Logger(WalletService.name);

  constructor(private readonly drizzle: DrizzleService) {}

  /**
   * Start a wallet top-up payment intent.
   * On webhook success: insert CREDIT_TOPUP entry into wallet.entry.
   * TODO: implement
   */
  async topUp(
    _userId: string,
    _dto: TopUpDto,
    _idempotencyKey: string,
  ): Promise<{ paymentIntent: unknown }> {
    throw new Error('WalletService.topUp not implemented — TODO');
  }

  /**
   * Return current balance (sum of delta_tnd) and cursor-paginated history.
   * TODO: SELECT SUM(delta_tnd), array of entries WHERE user_id = $1
   */
  async getBalance(_userId: string): Promise<{ balanceMillimes: bigint; entries: unknown[] }> {
    this.logger.debug({ userId: _userId }, 'getBalance (TODO)');
    return { balanceMillimes: 0n, entries: [] };
  }

  /**
   * Debit the wallet as part of a booking / order / pack transaction.
   * Called inside the booking transaction — must be called with an existing TX handle.
   * Throws InsufficientWalletException if balance < amount.
   * TODO: implement
   */
  async debit(
    _userId: string,
    _amountMillimes: bigint,
    _reason: string,
    _refType: string,
    _refId: string,
  ): Promise<void> {
    throw new Error('WalletService.debit not implemented — TODO');
  }
}

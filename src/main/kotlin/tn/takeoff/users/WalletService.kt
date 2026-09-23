package tn.takeoff.users

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tn.takeoff.common.errors.NotFoundException
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Single place that mutates a user's wallet balance and writes the ledger entry,
 * so every movement (admin credit/debit, refund, payment) stays in sync.
 */
@Service
class WalletService(
    private val users: UserRepository,
    private val ledger: WalletLedgerRepository,
) {

    @Transactional
    fun apply(
        userId: UUID,
        delta: BigDecimal,
        type: WalletEntryType,
        reason: String?,
        adminId: UUID? = null,
        refType: String? = null,
        refId: String? = null,
    ): BigDecimal {
        // Pessimistic lock: prevents concurrent balance reads from both seeing sufficient funds
        val u = users.findByIdForUpdate(userId).orElseThrow { NotFoundException("user", userId) }
        val newBalance = u.walletDt.add(delta)
        if (delta < BigDecimal.ZERO && newBalance < BigDecimal.ZERO) {
            throw tn.takeoff.common.errors.BadRequestException(
                "takeoff.wallet.insufficient_funds",
                "Insufficient wallet balance (available: ${u.walletDt} DT)"
            )
        }
        u.walletDt = newBalance
        u.updatedAt = Instant.now()
        users.save(u)
        ledger.save(WalletLedger(
            userId = userId,
            amountDt = delta,
            balanceAfter = newBalance,
            type = type,
            reason = reason,
            adminId = adminId,
            refType = refType,
            refId = refId,
        ))
        return newBalance
    }

    /**
     * Applies a movement at most once for the (userId, type, refType, refId) identity.
     *
     * Row locking alone only serialises concurrent requests — it does not stop a *sequential*
     * cancel → reopen → cancel from paying a refund twice. This makes the movement itself
     * idempotent, so repeated or replayed settlement attempts are no-ops.
     *
     * @return the new balance when applied, or null when the movement was already recorded.
     */
    @Transactional
    fun applyOnce(
        userId: UUID,
        delta: BigDecimal,
        type: WalletEntryType,
        reason: String?,
        adminId: UUID? = null,
        refType: String,
        refId: String,
    ): BigDecimal? {
        if (ledger.existsByUserIdAndTypeAndRefTypeAndRefId(userId, type, refType, refId)) return null
        return apply(userId, delta, type, reason, adminId, refType, refId)
    }
}

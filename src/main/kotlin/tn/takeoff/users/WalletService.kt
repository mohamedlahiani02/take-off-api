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
        val u = users.findById(userId).orElseThrow { NotFoundException("user", userId) }
        val newBalance = u.walletDt.add(delta)
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
}

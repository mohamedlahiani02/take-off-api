package tn.takeoff.users

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WalletLedgerRepository : JpaRepository<WalletLedger, UUID>, WalletLedgerGateway {
    override fun findByUserIdOrderByCreatedAtDesc(userId: UUID): List<WalletLedger>

    /**
     * Idempotency probe: has this exact movement already been recorded for this user?
     * (userId, type, refType, refId) is the refund/payment identity — see [WalletService.applyOnce].
     */
    fun existsByUserIdAndTypeAndRefTypeAndRefId(
        userId: UUID,
        type: WalletEntryType,
        refType: String,
        refId: String,
    ): Boolean
}

package tn.takeoff.users

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WalletLedgerRepository : JpaRepository<WalletLedger, UUID>, WalletLedgerGateway {
    override fun findByUserIdOrderByCreatedAtDesc(userId: UUID): List<WalletLedger>
}

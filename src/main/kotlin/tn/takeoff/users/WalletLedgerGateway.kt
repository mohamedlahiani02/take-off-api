package tn.takeoff.users

import java.util.UUID

interface WalletLedgerGateway {
    fun findByUserIdOrderByCreatedAtDesc(userId: UUID): List<WalletLedger>
}

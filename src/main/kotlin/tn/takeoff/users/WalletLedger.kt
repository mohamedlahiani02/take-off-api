package tn.takeoff.users

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class WalletEntryType { TOPUP, ADMIN_CREDIT, ADMIN_DEBIT, REFUND, PAYMENT }

@Entity
@Table(name = "wallet_ledger")
class WalletLedger(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "amount_dt", precision = 10, scale = 3, nullable = false)
    val amountDt: BigDecimal,

    @Column(name = "balance_after", precision = 10, scale = 3, nullable = false)
    val balanceAfter: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: WalletEntryType,

    @Column
    val reason: String? = null,

    @Column(name = "admin_id")
    val adminId: UUID? = null,

    @Column(name = "ref_type")
    val refType: String? = null,

    @Column(name = "ref_id")
    val refId: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)

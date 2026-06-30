package tn.takeoff.packs

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class PackActivity { PADEL, PILATES }

@Entity
@Table(name = "pack_types")
class PackType(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(nullable = false) var name: String,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var activity: PackActivity,
    @Column(name = "price_dt", precision = 10, scale = 3, nullable = false) var priceDt: BigDecimal,
    @Column(name = "credit_count") var creditCount: Int? = null,
    @Column(nullable = false) var unlimited: Boolean = false,
    @Column(name = "validity_months", nullable = false) var validityMonths: Int = 12,
    @Column(name = "display_order", nullable = false) var displayOrder: Int = 0,
    @Column(nullable = false) var active: Boolean = true,
    @Column(name = "created_at", nullable = false, updatable = false) val createdAt: Instant = Instant.now(),
)

enum class UserPackStatus { ACTIVE, EXPIRED, FROZEN, CANCELLED }
enum class PackSource { PURCHASE, ADMIN_ASSIGN }

@Entity
@Table(name = "user_packs")
class UserPack(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false) var userId: UUID,
    @Column(name = "pack_type_id", nullable = false) var packTypeId: UUID,
    @Column(name = "credits_remaining") var creditsRemaining: Int? = null,
    @Column(nullable = false) var unlimited: Boolean = false,
    @Column(name = "purchased_at", nullable = false) var purchasedAt: Instant = Instant.now(),
    @Column(name = "expires_at", nullable = false) var expiresAt: Instant,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: UserPackStatus = UserPackStatus.ACTIVE,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var source: PackSource = PackSource.PURCHASE,
    @Column(name = "assigned_by_admin_id") var assignedByAdminId: UUID? = null,
    @Column(name = "created_at", nullable = false, updatable = false) val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
)

enum class CreditEntryType { CONSUME, ADMIN_ADD, ADMIN_REMOVE, REFUND, EXPIRE }

@Entity
@Table(name = "pack_credit_ledger")
class PackCreditLedger(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "user_pack_id", nullable = false) var userPackId: UUID,
    @Column(nullable = false) var delta: Int,
    @Column(name = "credits_after") var creditsAfter: Int? = null,
    @Column var reason: String? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var type: CreditEntryType,
    @Column(name = "ref_type") var refType: String? = null,
    @Column(name = "ref_id") var refId: String? = null,
    @Column(name = "admin_id") var adminId: UUID? = null,
    @Column(name = "created_at", nullable = false, updatable = false) val createdAt: Instant = Instant.now(),
)

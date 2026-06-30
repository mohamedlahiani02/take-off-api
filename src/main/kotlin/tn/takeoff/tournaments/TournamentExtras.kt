package tn.takeoff.tournaments

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class DiscountType { FLAT, PERCENT }

@Entity
@Table(name = "tournament_pricing")
class TournamentPricing(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tournament_id", nullable = false)
    var tournamentId: UUID,

    @Column(nullable = false)
    var label: String,

    @Column(name = "price_dt", precision = 10, scale = 3, nullable = false)
    var priceDt: BigDecimal,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,
)

@Entity
@Table(name = "tournament_promo_codes")
class TournamentPromoCode(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tournament_id", nullable = false)
    var tournamentId: UUID,

    @Column(nullable = false)
    var code: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    var discountType: DiscountType,

    @Column(name = "discount_value", precision = 10, scale = 3, nullable = false)
    var discountValue: BigDecimal,

    @Column(name = "max_uses")
    var maxUses: Int? = null,

    @Column(nullable = false)
    var uses: Int = 0,

    @Column(name = "expires_at")
    var expiresAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)

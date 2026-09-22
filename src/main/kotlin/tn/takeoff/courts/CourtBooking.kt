package tn.takeoff.courts

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class BookingMode { SHARE, FULL }
enum class CourtPaymentStatus { PAID, PARTIAL, PAY_AT_CLUB, PENDING, REFUNDED }
enum class CourtPaymentMethod { D17, WALLET, CARD, CASH, PAY_AT_CLUB }
enum class BookingStatus { CONFIRMED, CANCELLED, COMPLETED, NO_SHOW }

@Entity
@Table(name = "court_bookings")
class CourtBooking(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "court_id", nullable = false)
    var courtId: UUID,

    @Column(name = "user_id")
    var userId: UUID? = null,

    @Column(name = "starts_at", nullable = false)
    var startsAt: Instant,

    @Column(name = "ends_at", nullable = false)
    var endsAt: Instant,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var mode: BookingMode,

    @Column(name = "price_dt", precision = 10, scale = 3, nullable = false)
    var priceDt: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    var paymentStatus: CourtPaymentStatus = CourtPaymentStatus.PENDING,

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    var paymentMethod: CourtPaymentMethod? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: BookingStatus = BookingStatus.CONFIRMED,

    @Column(name = "user_pack_id")
    var userPackId: UUID? = null,

    @Column(name = "created_by_admin_id")
    var createdByAdminId: UUID? = null,

    @Column(name = "cancel_reason")
    var cancelReason: String? = null,

    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

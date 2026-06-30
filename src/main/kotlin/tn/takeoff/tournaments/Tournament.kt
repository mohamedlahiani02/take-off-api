package tn.takeoff.tournaments

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class TournamentFormat { AMERICANO, KNOCKOUT, ROUND_ROBIN, SWISS }
enum class TournamentCategory { MIXED, MEN, WOMEN, JUNIOR, SENIOR }
enum class TournamentStatus { DRAFT, PUBLISHED, REGISTRATION_OPEN, REGISTRATION_CLOSED, ONGOING, FINISHED }
enum class RegistrationMode { OPEN, MEMBERS_ONLY, INVITATION_ONLY }
enum class PaymentRule { ONLINE, AT_CLUB, BOTH }

@Entity
@Table(name = "tournaments")
class Tournament(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var title: String,

    @Column(columnDefinition = "text")
    var description: String? = null,

    @Column(name = "banner_url")
    var bannerUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var format: TournamentFormat,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var category: TournamentCategory,

    @Column(name = "starts_at", nullable = false)
    var startsAt: Instant,

    @Column(name = "ends_at")
    var endsAt: Instant? = null,

    @Column(name = "entry_fee_dt", precision = 10, scale = 3, nullable = false)
    var entryFeeDt: BigDecimal = BigDecimal.ZERO,

    @Column
    var prize: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: TournamentStatus = TournamentStatus.DRAFT,

    @Column(name = "max_participants")
    var maxParticipants: Int? = null,

    @Column(name = "registration_deadline")
    var registrationDeadline: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_mode", nullable = false)
    var registrationMode: RegistrationMode = RegistrationMode.OPEN,

    @Column(name = "auto_waitlist", nullable = false)
    var autoWaitlist: Boolean = false,

    @Column(name = "manual_validation", nullable = false)
    var manualValidation: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_rule", nullable = false)
    var paymentRule: PaymentRule = PaymentRule.BOTH,

    @Column(name = "created_by_admin_id")
    var createdByAdminId: UUID? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

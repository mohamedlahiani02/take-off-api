package tn.takeoff.users

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class UserRole { USER, ADMIN }

enum class AccountStatus { ACTIVE, GHOST, BLOCKED, DELETED }

@Entity
@Table(name = "users")
class User(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(unique = true, nullable = true)
    var email: String?,

    @Column(name = "password_hash", nullable = true)
    var passwordHash: String?,

    @Column(nullable = false)
    var name: String,

    @Column(unique = true, nullable = false)
    var phone: String,

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    var tracks: Array<String> = arrayOf(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: UserRole = UserRole.USER,

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false)
    var accountStatus: AccountStatus = AccountStatus.ACTIVE,

    @Column(name = "created_by_admin_id")
    var createdByAdminId: UUID? = null,

    @Column(name = "wallet_dt", precision = 10, scale = 3)
    var walletDt: BigDecimal = BigDecimal.ZERO,

    var points: Int = 0,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

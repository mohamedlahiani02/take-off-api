package tn.takeoff.users

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class UserRole { USER, ADMIN }

@Entity
@Table(name = "users")
class User(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(unique = true, nullable = false)
    val email: String,

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,

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

    @Column(name = "wallet_dt", precision = 10, scale = 3)
    var walletDt: BigDecimal = BigDecimal.ZERO,

    @Column(name = "padel_level")
    var padelLevel: Int = 1,

    @Column(name = "padel_level_self_declared")
    var padelLevelSelfDeclared: Int? = null,

    var points: Int = 100,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

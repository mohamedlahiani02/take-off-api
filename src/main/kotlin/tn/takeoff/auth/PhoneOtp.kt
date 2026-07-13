package tn.takeoff.auth

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "phone_otps")
class PhoneOtp(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    val phone: String,

    @Column(name = "code_hash", nullable = false)
    val codeHash: String,

    @Column(nullable = false)
    var used: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
)

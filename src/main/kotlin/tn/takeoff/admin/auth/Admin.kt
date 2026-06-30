package tn.takeoff.admin.auth

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "admins")
class Admin(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(unique = true, nullable = false)
    val email: String,

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,

    @Column(nullable = false)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "admin_role", nullable = false)
    var role: AdminRole = AdminRole.RECEPTION,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

package tn.takeoff.admin.auth

import java.util.Optional
import java.util.UUID

interface AdminGateway {
    fun findByEmail(email: String): Optional<Admin>
    fun findById(id: UUID): Optional<Admin>
    fun save(admin: Admin): Admin
    fun existsByEmail(email: String): Boolean
    fun count(): Long
    fun findAllByOrderByCreatedAtAsc(): List<Admin>
    fun countByRoleAndActive(role: AdminRole, active: Boolean): Long
}

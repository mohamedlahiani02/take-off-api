package tn.takeoff.admin.auth

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface AdminRepository : JpaRepository<Admin, UUID>, AdminGateway {
    override fun findByEmail(email: String): Optional<Admin>
    override fun existsByEmail(email: String): Boolean
}

package tn.takeoff.users

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID>, UserGateway {
    override fun findByEmail(email: String): Optional<User>
    override fun existsByEmail(email: String): Boolean
    override fun findByPhone(phone: String): Optional<User>
    override fun existsByPhone(phone: String): Boolean
}

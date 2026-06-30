package tn.takeoff.users

import java.util.Optional
import java.util.UUID

interface UserGateway {
    fun findById(id: UUID): Optional<User>
    fun findByEmail(email: String): Optional<User>
    fun existsByEmail(email: String): Boolean
    fun findByPhone(phone: String): Optional<User>
    fun existsByPhone(phone: String): Boolean
    fun save(user: User): User
}

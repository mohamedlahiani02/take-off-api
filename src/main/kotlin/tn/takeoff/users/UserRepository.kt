package tn.takeoff.users

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID>, UserGateway {
    override fun findByEmail(email: String): Optional<User>
    override fun existsByEmail(email: String): Boolean
    override fun findByPhone(phone: String): Optional<User>
    override fun existsByPhone(phone: String): Boolean

    // ── admin (Epic B) ──
    @Query(
        """
        SELECT u FROM User u
        WHERE u.accountStatus <> tn.takeoff.users.AccountStatus.DELETED
          AND (LOWER(u.name) LIKE LOWER(CONCAT('%', :term, '%'))
               OR u.phone LIKE CONCAT('%', :term, '%')
               OR LOWER(u.email) LIKE LOWER(CONCAT('%', :term, '%')))
        ORDER BY u.createdAt DESC
        """,
    )
    fun search(@Param("term") term: String, pageable: Pageable): Page<User>

    fun findByAccountStatus(accountStatus: AccountStatus, pageable: Pageable): Page<User>

    fun findByAccountStatusNot(accountStatus: AccountStatus, pageable: Pageable): Page<User>
}

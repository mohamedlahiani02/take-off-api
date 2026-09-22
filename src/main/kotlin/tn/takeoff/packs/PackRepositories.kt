package tn.takeoff.packs

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.Optional
import java.util.UUID

interface PackTypeRepository : JpaRepository<PackType, UUID> {
    fun findAllByOrderByActivityAscDisplayOrderAsc(): List<PackType>
}

interface UserPackRepository : JpaRepository<UserPack, UUID> {
    fun findByUserIdOrderByPurchasedAtDesc(userId: UUID): List<UserPack>
    fun findByStatusOrderByExpiresAtAsc(status: UserPackStatus): List<UserPack>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM UserPack p WHERE p.id = :id")
    fun findByIdForUpdate(id: UUID): Optional<UserPack>
}

interface PackCreditLedgerRepository : JpaRepository<PackCreditLedger, UUID> {
    fun findByUserPackIdOrderByCreatedAtDesc(userPackId: UUID): List<PackCreditLedger>
}

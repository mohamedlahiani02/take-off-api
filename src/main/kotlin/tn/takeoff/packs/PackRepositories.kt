package tn.takeoff.packs

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PackTypeRepository : JpaRepository<PackType, UUID> {
    fun findAllByOrderByActivityAscDisplayOrderAsc(): List<PackType>
}

interface UserPackRepository : JpaRepository<UserPack, UUID> {
    fun findByUserIdOrderByPurchasedAtDesc(userId: UUID): List<UserPack>
    fun findByStatusOrderByExpiresAtAsc(status: UserPackStatus): List<UserPack>
}

interface PackCreditLedgerRepository : JpaRepository<PackCreditLedger, UUID> {
    fun findByUserPackIdOrderByCreatedAtDesc(userPackId: UUID): List<PackCreditLedger>
}

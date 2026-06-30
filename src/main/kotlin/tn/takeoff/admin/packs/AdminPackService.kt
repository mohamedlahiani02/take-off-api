package tn.takeoff.admin.packs

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tn.takeoff.admin.audit.AuditService
import tn.takeoff.common.errors.BadRequestException
import tn.takeoff.common.errors.NotFoundException
import tn.takeoff.packs.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class AdminPackService(
    private val packTypes: PackTypeRepository,
    private val userPacks: UserPackRepository,
    private val ledger: PackCreditLedgerRepository,
    private val auditService: AuditService,
) {

    // ── pack types (F-01/02/07) ──
    fun listTypes(): List<PackType> = packTypes.findAllByOrderByActivityAscDisplayOrderAsc()

    @Transactional
    fun createType(req: PackTypeRequest, adminId: UUID): PackType {
        val p = PackType(
            name = req.name, activity = req.activity, priceDt = req.priceDt,
            creditCount = if (req.unlimited) null else req.creditCount, unlimited = req.unlimited,
            validityMonths = req.validityMonths, displayOrder = req.displayOrder, active = req.active,
        )
        packTypes.save(p)
        auditService.log(adminId, "pack.type_create", "pack_type", p.id.toString())
        return p
    }

    @Transactional
    fun updateType(id: UUID, req: PackTypeRequest, adminId: UUID): PackType {
        val p = packTypes.findById(id).orElseThrow { NotFoundException("pack_type", id) }
        p.name = req.name; p.activity = req.activity; p.priceDt = req.priceDt
        p.creditCount = if (req.unlimited) null else req.creditCount; p.unlimited = req.unlimited
        p.validityMonths = req.validityMonths; p.displayOrder = req.displayOrder; p.active = req.active
        packTypes.save(p)
        auditService.log(adminId, "pack.type_update", "pack_type", id.toString())
        return p
    }

    // ── user packs ──
    fun activeUserPacks(): List<UserPack> = userPacks.findByStatusOrderByExpiresAtAsc(UserPackStatus.ACTIVE)
    fun userPacksFor(userId: UUID): List<UserPack> = userPacks.findByUserIdOrderByPurchasedAtDesc(userId)

    /** F-04: assign a pack to a user (cash payment received at reception). */
    @Transactional
    fun assign(req: AssignPackRequest, adminId: UUID): UserPack {
        val type = packTypes.findById(req.packTypeId).orElseThrow { NotFoundException("pack_type", req.packTypeId) }
        val now = Instant.now()
        val up = UserPack(
            userId = req.userId, packTypeId = type.id,
            creditsRemaining = if (type.unlimited) null else type.creditCount,
            unlimited = type.unlimited,
            purchasedAt = now, expiresAt = now.plus(type.validityMonths * 30L, ChronoUnit.DAYS),
            source = PackSource.ADMIN_ASSIGN, assignedByAdminId = adminId,
        )
        userPacks.save(up)
        auditService.log(adminId, "pack.assign", "user_pack", up.id.toString(), mapOf("userId" to req.userId.toString(), "packType" to type.name))
        return up
    }

    /** F-05: extend expiry with a reason. */
    @Transactional
    fun extend(userPackId: UUID, newExpiresAt: Instant, reason: String, adminId: UUID): UserPack {
        val up = userPacks.findById(userPackId).orElseThrow { NotFoundException("user_pack", userPackId) }
        up.expiresAt = newExpiresAt
        if (up.status == UserPackStatus.EXPIRED) up.status = UserPackStatus.ACTIVE
        up.updatedAt = Instant.now()
        userPacks.save(up)
        auditService.log(adminId, "pack.extend", "user_pack", userPackId.toString(), mapOf("reason" to reason))
        return up
    }

    /** F-06: add or remove credits (compensation / correction). */
    @Transactional
    fun adjustCredits(userPackId: UUID, delta: Int, reason: String, adminId: UUID): UserPack {
        val up = userPacks.findById(userPackId).orElseThrow { NotFoundException("user_pack", userPackId) }
        if (up.unlimited) throw BadRequestException("takeoff.pack.unlimited", "Unlimited packs have no credit count")
        val current = up.creditsRemaining ?: 0
        val next = current + delta
        if (next < 0) throw BadRequestException("takeoff.pack.negative", "Resulting credits would be negative")
        up.creditsRemaining = next; up.updatedAt = Instant.now()
        userPacks.save(up)
        ledger.save(PackCreditLedger(
            userPackId = userPackId, delta = delta, creditsAfter = next, reason = reason,
            type = if (delta >= 0) CreditEntryType.ADMIN_ADD else CreditEntryType.ADMIN_REMOVE, adminId = adminId,
        ))
        auditService.log(adminId, "pack.adjust_credits", "user_pack", userPackId.toString(), mapOf("delta" to delta, "reason" to reason))
        return up
    }

    @Transactional
    fun setStatus(userPackId: UUID, status: UserPackStatus, adminId: UUID): UserPack {
        val up = userPacks.findById(userPackId).orElseThrow { NotFoundException("user_pack", userPackId) }
        up.status = status; up.updatedAt = Instant.now()
        userPacks.save(up)
        auditService.log(adminId, "pack.status", "user_pack", userPackId.toString(), mapOf("status" to status.name))
        return up
    }
}

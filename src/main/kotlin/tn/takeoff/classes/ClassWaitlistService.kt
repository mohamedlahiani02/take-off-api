package tn.takeoff.classes

import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tn.takeoff.admin.audit.AuditService
import tn.takeoff.common.errors.BadRequestException
import tn.takeoff.common.errors.ConflictException
import tn.takeoff.common.errors.NotFoundException
import tn.takeoff.packs.CreditEntryType
import tn.takeoff.packs.PackActivity
import tn.takeoff.packs.PackCreditLedger
import tn.takeoff.packs.PackCreditLedgerRepository
import tn.takeoff.packs.PackTypeRepository
import tn.takeoff.packs.UserPackRepository
import tn.takeoff.packs.UserPackStatus
import tn.takeoff.users.WalletEntryType
import tn.takeoff.users.WalletService
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Owns every state transition a waitlisted booking can go through once a
 * spot opens up: propose it to one member, let them confirm (which is the
 * only moment money or a pack credit ever moves) or decline, expire a
 * proposal nobody answered, and chain to the next waitlisted member in
 * either case. Shared by the admin "promote" action (which only ever
 * *proposes* now) and the member-facing confirm/decline endpoints, so
 * there is exactly one place this logic lives — no second path can
 * reach BOOKED without going through confirmProposal's real payment
 * resolution.
 */
@Service
class ClassWaitlistService(
    private val sessions: ClassSessionRepository,
    private val bookings: ClassBookingRepository,
    private val userPacks: UserPackRepository,
    private val ledger: PackCreditLedgerRepository,
    private val packTypes: PackTypeRepository,
    private val walletService: WalletService,
    private val auditService: AuditService,
    private val em: EntityManager,
    @Value("\${class.promotion.hold-minutes:30}") private val holdMinutes: Long,
) {
    companion object {
        /** A spot is "occupied" — unavailable to a fresh booker — while someone
         *  holds it outright or is deciding whether to keep it. */
        val OCCUPYING_STATUSES = setOf(ClassBookingStatus.BOOKED, ClassBookingStatus.ATTENDED, ClassBookingStatus.PROPOSED)
    }

    /**
     * Admin-triggered (or chained, see [proposeNext]) step 1: offer the spot,
     * charge nothing. Mirrors the locking promote() used before this policy
     * change — booking row first (so a double-click/retried propose on the
     * same booking is rejected rather than re-offered), then the session row
     * (status/start time/real capacity re-checked under that lock).
     */
    @Transactional
    fun propose(bookingId: UUID, adminId: UUID?): ClassBooking {
        val b = bookings.findByIdForUpdate(bookingId).orElseThrow { NotFoundException("class_booking", bookingId) }
        if (b.status != ClassBookingStatus.WAITLIST) throw ConflictException("takeoff.class.not_waitlisted", "Not on the waitlist")
        b.userId ?: throw BadRequestException("takeoff.class.no_user", "Waitlisted booking has no member")

        val session = sessions.findByIdForUpdate(b.sessionId).orElseThrow { NotFoundException("class_session", b.sessionId) }
        if (session.status != SessionStatus.SCHEDULED)
            throw BadRequestException("takeoff.class.cancelled", "This session has been cancelled")
        if (session.startsAt.isBefore(Instant.now()))
            throw BadRequestException("takeoff.class.past_session", "Cannot book a session that has already started")

        val occupied = bookings.findBySessionId(session.id).count { it.status in OCCUPYING_STATUSES }
        if (occupied >= session.maxSpots)
            throw BadRequestException("takeoff.class.full", "Session is already full")

        b.status = ClassBookingStatus.PROPOSED
        b.proposalExpiresAt = Instant.now().plusSeconds(holdMinutes * 60)
        b.waitlistPosition = null
        b.updatedAt = Instant.now()
        bookings.save(b)
        if (adminId != null) auditService.log(adminId, "class.propose", "class_booking", bookingId.toString(), mapOf("holdMinutes" to holdMinutes))
        return b
    }

    /**
     * Step 2, the only moment a proposal becomes real: re-validate pack and
     * capacity *now* (not at proposal time — both can have changed during the
     * hold window) under lock, then resolve payment exactly as a fresh
     * book() would. If the pack that was valid when proposed has since
     * expired/emptied, this does NOT silently fall back to the wallet — the
     * member must explicitly pass paymentMethod=WALLET, same rule book()
     * already enforces, so a payment method change always gets a fresh
     * explicit confirmation rather than a silent substitution.
     */
    @Transactional
    fun confirmProposal(
        bookingId: UUID,
        userId: UUID,
        paymentMethod: MemberClassController.ClassPaymentMethod?,
        quotedPriceDt: BigDecimal?,
    ): ClassBooking {
        val b = bookings.findByIdForUpdate(bookingId).orElseThrow { NotFoundException("class_booking", bookingId) }
        if (b.userId != userId) throw BadRequestException("takeoff.forbidden", "Not your booking")
        expireIfDue(b)
        if (b.status != ClassBookingStatus.PROPOSED)
            throw ConflictException("takeoff.class.no_active_proposal", "No active proposal to confirm")

        val session = sessions.findByIdForUpdate(b.sessionId).orElseThrow { NotFoundException("class_session", b.sessionId) }
        if (session.status != SessionStatus.SCHEDULED)
            throw BadRequestException("takeoff.class.cancelled", "This session has been cancelled")

        val activePack = userPacks.findByUserIdOrderByPurchasedAtDesc(userId)
            .firstOrNull {
                it.status == UserPackStatus.ACTIVE && it.expiresAt.isAfter(Instant.now()) &&
                (it.unlimited || (it.creditsRemaining ?: 0) > 0) &&
                packTypes.findById(it.packTypeId).map { pt -> pt.activity == PackActivity.PILATES }.orElse(false)
            }
            ?.let { candidate ->
                val locked = userPacks.findByIdForUpdate(candidate.id).orElse(null) ?: return@let null
                em.refresh(locked)
                locked.takeIf {
                    it.status == UserPackStatus.ACTIVE && it.expiresAt.isAfter(Instant.now()) &&
                    (it.unlimited || (it.creditsRemaining ?: 0) > 0) &&
                    packTypes.findById(it.packTypeId).map { pt -> pt.activity == PackActivity.PILATES }.orElse(false)
                }
            }

        if (activePack != null) {
            b.paidWith = if (activePack.unlimited) PaidWith.UNLIMITED else PaidWith.PACK
            b.userPackId = activePack.id
            b.priceDt = BigDecimal.ZERO
            if (!activePack.unlimited) {
                val remaining = (activePack.creditsRemaining ?: 0) - 1
                activePack.creditsRemaining = remaining
                if (remaining <= 0) activePack.status = UserPackStatus.EXPIRED
                userPacks.save(activePack)
                ledger.save(PackCreditLedger(
                    userPackId = activePack.id, delta = -1,
                    type = CreditEntryType.CONSUME, reason = "class_booking_promote_confirm",
                    refType = "class_session", refId = session.id.toString(),
                ))
            }
        } else {
            if (paymentMethod != MemberClassController.ClassPaymentMethod.WALLET) {
                throw BadRequestException(
                    "takeoff.class.payment_required",
                    "No valid pack for this class — choose a payment method to confirm this booking",
                )
            }
            if (quotedPriceDt != null && quotedPriceDt.compareTo(session.priceDt) != 0) {
                throw BadRequestException("takeoff.class.price_mismatch", "The price has changed — reload and confirm again")
            }
            walletService.applyOnce(
                userId = userId, delta = session.priceDt.negate(), type = WalletEntryType.PAYMENT,
                reason = "class_single_session", refType = "class_booking", refId = b.id.toString(),
            )
            b.paidWith = PaidWith.SINGLE
            b.priceDt = session.priceDt
        }

        b.status = ClassBookingStatus.BOOKED
        b.proposalExpiresAt = null
        b.updatedAt = Instant.now()
        bookings.save(b)
        return b
    }

    /** The member explicitly says no: nothing was ever charged, so there is
     *  nothing to undo — just free the spot for the next person in line. */
    @Transactional
    fun declineProposal(bookingId: UUID, userId: UUID): ClassBooking {
        val b = bookings.findByIdForUpdate(bookingId).orElseThrow { NotFoundException("class_booking", bookingId) }
        if (b.userId != userId) throw BadRequestException("takeoff.forbidden", "Not your booking")
        expireIfDue(b)
        if (b.status != ClassBookingStatus.PROPOSED)
            throw ConflictException("takeoff.class.no_active_proposal", "No active proposal to decline")

        b.status = ClassBookingStatus.DECLINED
        b.proposalExpiresAt = null
        b.updatedAt = Instant.now()
        bookings.save(b)
        proposeNext(b.sessionId)
        return b
    }

    /** Lazily expires one booking if its hold has passed; called before
     *  acting on a proposal so a stale confirm/decline gets the right error
     *  instead of silently succeeding past the deadline. */
    private fun expireIfDue(b: ClassBooking) {
        if (b.status == ClassBookingStatus.PROPOSED && b.proposalExpiresAt?.isBefore(Instant.now()) == true) {
            b.status = ClassBookingStatus.EXPIRED
            b.proposalExpiresAt = null
            b.updatedAt = Instant.now()
            bookings.save(b)
            proposeNext(b.sessionId)
        }
    }

    /**
     * Sweeps every proposal whose hold has passed, in production this runs
     * both on a timer (below) and lazily from the member-facing read paths
     * (MemberClassController.schedule/myBookings) so a member who checks
     * right after their own window closes sees it reflected immediately
     * rather than waiting for the next scheduled tick.
     */
    @Transactional
    fun expireStaleProposals(): Int {
        val due = bookings.findByStatusAndProposalExpiresAtBefore(ClassBookingStatus.PROPOSED, Instant.now())
        var count = 0
        for (stale in due) {
            val locked = bookings.findByIdForUpdate(stale.id).orElse(null) ?: continue
            if (locked.status != ClassBookingStatus.PROPOSED) continue
            if (locked.proposalExpiresAt == null || locked.proposalExpiresAt!!.isAfter(Instant.now())) continue
            locked.status = ClassBookingStatus.EXPIRED
            locked.proposalExpiresAt = null
            locked.updatedAt = Instant.now()
            bookings.save(locked)
            proposeNext(locked.sessionId)
            count++
        }
        return count
    }

    @Scheduled(fixedDelayString = "\${class.promotion.sweep-interval-ms:60000}")
    fun scheduledSweep() { expireStaleProposals() }

    /** After a decline/expiry/cancellation frees a held spot, offer it to
     *  whoever is next on the waitlist — never left to sit open while
     *  someone is still waiting. Silently does nothing if the session is no
     *  longer bookable or nobody is left waiting; the spot just reopens for
     *  a fresh booking. */
    fun proposeNext(sessionId: UUID) {
        val next = bookings.findFirstBySessionIdAndStatusOrderByWaitlistPositionAsc(sessionId, ClassBookingStatus.WAITLIST)
            .orElse(null) ?: return
        try {
            propose(next.id, adminId = null)
        } catch (_: BadRequestException) {
            // Session cancelled/started/still full by the time we got here —
            // leave this member on the waitlist rather than erroring the
            // caller's own request (a decline/expiry must still succeed).
        }
    }
}

package tn.takeoff.admin.users

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tn.takeoff.admin.audit.AuditService
import tn.takeoff.auth.RefreshTokenGateway
import tn.takeoff.common.PhoneUtil
import tn.takeoff.common.errors.BadRequestException
import tn.takeoff.common.errors.ConflictException
import tn.takeoff.common.errors.NotFoundException
import tn.takeoff.users.AccountStatus
import tn.takeoff.users.User
import tn.takeoff.users.UserRepository
import tn.takeoff.users.WalletEntryType
import tn.takeoff.users.WalletLedger
import tn.takeoff.users.WalletLedgerRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class AdminUserService(
    private val users: UserRepository,
    private val walletLedger: WalletLedgerRepository,
    private val auditService: AuditService,
    private val refreshTokens: RefreshTokenGateway,
) {

    fun search(term: String): List<UserSummaryDto> {
        if (term.isBlank()) return emptyList()
        val page = PageRequest.of(0, 10)
        return users.search(term.trim(), page).content.map(UserSummaryDto::from)
    }

    fun list(status: AccountStatus?, page: Int, size: Int): Page<UserSummaryDto> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = if (status != null) {
            users.findByAccountStatus(status, pageable)
        } else {
            users.findByAccountStatusNot(AccountStatus.DELETED, pageable)
        }
        return result.map(UserSummaryDto::from)
    }

    fun getProfile(id: UUID): UserProfileDto {
        val u = users.findById(id).orElseThrow { NotFoundException("user", id) }
        val ledger = walletLedger.findByUserIdOrderByCreatedAtDesc(id).map(WalletLedgerDto::from)
        return UserProfileDto(
            user = UserSummaryDto.from(u),
            tracks = u.tracks.toList(),
            points = u.points,
            walletLedger = ledger,
        )
    }

    /** B-02: create a ghost user (name + phone, optional email, no password). */
    @Transactional
    fun createGhost(req: CreateGhostRequest, adminId: UUID): UserSummaryDto {
        val phone = PhoneUtil.normalize(req.phone)
        if (users.existsByPhone(phone)) {
            throw ConflictException("takeoff.user.phone_taken", "A user with this phone already exists")
        }
        val id = UUID.randomUUID()
        val email = req.email?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        if (email != null && users.existsByEmail(email)) {
            throw ConflictException("takeoff.user.email_taken", "Email already in use")
        }
        val user = User(
            id = id,
            email = email,
            passwordHash = null,
            name = req.name.trim(),
            phone = phone,
            accountStatus = AccountStatus.GHOST,
            createdByAdminId = adminId,
        )
        users.save(user)
        auditService.log(adminId, "user.create_ghost", "user", id.toString())
        return UserSummaryDto.from(user)
    }

    /** B-05: edit name / phone / email. */
    @Transactional
    fun update(id: UUID, req: UpdateUserRequest, adminId: UUID): UserSummaryDto {
        val u = users.findById(id).orElseThrow { NotFoundException("user", id) }
        req.name?.trim()?.takeIf { it.isNotBlank() }?.let { u.name = it }
        req.phone?.let {
            val phone = PhoneUtil.normalize(it)
            if (phone != u.phone && users.existsByPhone(phone)) {
                throw ConflictException("takeoff.user.phone_taken", "A user with this phone already exists")
            }
            u.phone = phone
        }
        req.email?.trim()?.lowercase()?.takeIf { it.isNotBlank() }?.let {
            if (it != u.email && users.existsByEmail(it)) {
                throw ConflictException("takeoff.user.email_taken", "Email already in use")
            }
            u.email = it
        }
        u.updatedAt = Instant.now()
        users.save(u)
        auditService.log(adminId, "user.update", "user", id.toString())
        return UserSummaryDto.from(u)
    }

    /** B-06: credit (amount > 0) or debit (applied as negative) the wallet, with a reason. */
    @Transactional
    fun adjustWallet(id: UUID, amountDt: BigDecimal, reason: String, credit: Boolean, adminId: UUID): UserSummaryDto {
        if (amountDt.signum() <= 0) throw BadRequestException("takeoff.wallet.bad_amount", "Amount must be positive")
        val u = users.findById(id).orElseThrow { NotFoundException("user", id) }
        val delta = if (credit) amountDt else amountDt.negate()
        val newBalance = u.walletDt.add(delta)
        if (newBalance.signum() < 0) {
            throw BadRequestException("takeoff.wallet.insufficient", "Resulting balance would be negative")
        }
        u.walletDt = newBalance
        u.updatedAt = Instant.now()
        users.save(u)
        walletLedger.save(WalletLedger(
            userId = id,
            amountDt = delta,
            balanceAfter = newBalance,
            type = if (credit) WalletEntryType.ADMIN_CREDIT else WalletEntryType.ADMIN_DEBIT,
            reason = reason,
            adminId = adminId,
        ))
        auditService.log(
            adminId,
            if (credit) "user.wallet_credit" else "user.wallet_debit",
            "user", id.toString(),
            mapOf("amount" to delta.toPlainString(), "reason" to reason),
        )
        return UserSummaryDto.from(u)
    }

    /** B-10: block / unblock login. */
    @Transactional
    fun setBlocked(id: UUID, blocked: Boolean, adminId: UUID): UserSummaryDto {
        val u = users.findById(id).orElseThrow { NotFoundException("user", id) }
        u.accountStatus = if (blocked) AccountStatus.BLOCKED else AccountStatus.ACTIVE
        u.updatedAt = Instant.now()
        users.save(u)
        if (blocked) refreshTokens.deleteAllByUserId(id)
        auditService.log(adminId, if (blocked) "user.block" else "user.unblock", "user", id.toString())
        return UserSummaryDto.from(u)
    }

    /** B-11: soft-delete (GDPR). Keeps the row but blocks all access. */
    @Transactional
    fun softDelete(id: UUID, adminId: UUID) {
        val u = users.findById(id).orElseThrow { NotFoundException("user", id) }
        u.accountStatus = AccountStatus.DELETED
        u.updatedAt = Instant.now()
        users.save(u)
        refreshTokens.deleteAllByUserId(id)
        auditService.log(adminId, "user.soft_delete", "user", id.toString())
    }

    private companion object {
        // Not a valid BCrypt hash, so passwordEncoder.matches() always returns false.
        const val UNUSABLE_PASSWORD = "!"
    }
}

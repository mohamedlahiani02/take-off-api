package tn.takeoff.admin.users

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import tn.takeoff.users.AccountStatus
import tn.takeoff.users.User
import tn.takeoff.users.UserRole
import tn.takeoff.users.WalletEntryType
import tn.takeoff.users.WalletLedger
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class UserSummaryDto(
    val id: UUID,
    val name: String,
    val phone: String,
    val email: String?,
    val accountStatus: AccountStatus,
    val role: UserRole,
    val walletDt: BigDecimal,
    val createdAt: Instant,
) {
    companion object {
        fun from(u: User) = UserSummaryDto(
            id = u.id,
            name = u.name,
            phone = u.phone,
            email = if (u.email.endsWith("@ghost.takeoff.local")) null else u.email,
            accountStatus = u.accountStatus,
            role = u.role,
            walletDt = u.walletDt,
            createdAt = u.createdAt,
        )
    }
}

data class WalletLedgerDto(
    val amountDt: BigDecimal,
    val balanceAfter: BigDecimal,
    val type: WalletEntryType,
    val reason: String?,
    val refType: String?,
    val refId: String?,
    val createdAt: Instant,
) {
    companion object {
        fun from(l: WalletLedger) = WalletLedgerDto(
            amountDt = l.amountDt,
            balanceAfter = l.balanceAfter,
            type = l.type,
            reason = l.reason,
            refType = l.refType,
            refId = l.refId,
            createdAt = l.createdAt,
        )
    }
}

/** Full profile. Bookings/packs lists are populated as those services come online. */
data class UserProfileDto(
    val user: UserSummaryDto,
    val tracks: List<String>,
    val points: Int,
    val walletLedger: List<WalletLedgerDto>,
)

data class CreateGhostRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val phone: String,
    val email: String? = null,
)

data class UpdateUserRequest(
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
)

data class WalletAdjustRequest(
    @field:NotNull @field:Positive val amountDt: BigDecimal,
    @field:NotBlank val reason: String,
)

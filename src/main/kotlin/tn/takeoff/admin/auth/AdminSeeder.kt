package tn.takeoff.admin.auth

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class AdminSeeder(
    private val adminRepo: AdminGateway,
    private val passwordEncoder: PasswordEncoder,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val email = (System.getenv("ADMIN_EMAIL") ?: return).trim().lowercase()
        val password = System.getenv("ADMIN_PASSWORD") ?: return

        val existing = adminRepo.findByEmail(email).orElse(null)
        if (existing == null) {
            adminRepo.save(Admin(
                email = email,
                passwordHash = passwordEncoder.encode(password),
                name = "Super Admin",
                role = AdminRole.SUPER_ADMIN,
            ))
            return
        }

        // ADMIN_PASSWORD is the source of truth: re-sync the hash on boot so a
        // changed env var always takes effect. Also ensure the account is active
        // and retains SUPER_ADMIN.
        existing.passwordHash = passwordEncoder.encode(password)
        existing.active = true
        existing.role = AdminRole.SUPER_ADMIN
        existing.updatedAt = Instant.now()
        adminRepo.save(existing)
    }
}

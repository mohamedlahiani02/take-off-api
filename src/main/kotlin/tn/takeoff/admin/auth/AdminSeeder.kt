package tn.takeoff.admin.auth

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class AdminSeeder(
    private val adminRepo: AdminGateway,
    private val passwordEncoder: PasswordEncoder,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        if (adminRepo.count() > 0) return
        val email = System.getenv("ADMIN_EMAIL") ?: return
        val password = System.getenv("ADMIN_PASSWORD") ?: return
        adminRepo.save(Admin(
            email = email.trim().lowercase(),
            passwordHash = passwordEncoder.encode(password),
            name = "Super Admin",
            role = AdminRole.SUPER_ADMIN,
        ))
    }
}

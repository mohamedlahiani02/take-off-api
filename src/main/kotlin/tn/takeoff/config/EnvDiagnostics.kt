package tn.takeoff.config

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Logs (at startup) whether critical env vars are visible to the process.
 * Prints presence and length only — never the secret value itself.
 * Helps diagnose Railway/host variable-scoping issues without exposing secrets.
 */
@Component
@Order(0)
class EnvDiagnostics : ApplicationRunner {

    private val log = LoggerFactory.getLogger(EnvDiagnostics::class.java)

    override fun run(args: ApplicationArguments) {
        report("JWT_PRIVATE_KEY")
        report("JWT_PUBLIC_KEY")
        report("ADMIN_EMAIL")
        report("ADMIN_PASSWORD")
    }

    private fun report(name: String) {
        val v = System.getenv(name)
        if (v == null) {
            log.warn("ENV-CHECK {} = MISSING (System.getenv returned null)", name)
        } else {
            log.warn("ENV-CHECK {} = PRESENT (length={})", name, v.length)
        }
    }
}

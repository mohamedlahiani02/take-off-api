package tn.takeoff.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.springframework.stereotype.Service
import tn.takeoff.admin.auth.AdminRole
import tn.takeoff.config.JwtProperties
import tn.takeoff.users.UserRole
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

@Service
class JwtService(private val props: JwtProperties) {

    private val algorithm: Algorithm by lazy {
        val kf = KeyFactory.getInstance("RSA")

        val privateRaw = System.getenv("JWT_PRIVATE_KEY")
            ?: Files.readString(Paths.get(props.privateKeyPath))
        val privateKey = kf.generatePrivate(PKCS8EncodedKeySpec(decodePem(privateRaw))) as RSAPrivateKey

        val publicRaw = System.getenv("JWT_PUBLIC_KEY")
            ?: Files.readString(Paths.get(props.publicKeyPath))
        val publicKey = kf.generatePublic(X509EncodedKeySpec(decodePem(publicRaw))) as RSAPublicKey

        Algorithm.RSA256(publicKey, privateKey)
    }

    /**
     * Decode a PEM-encoded key into DER bytes, tolerant of how env vars mangle PEMs:
     * literal "\n" sequences, surrounding quotes, any BEGIN/END header variant, and
     * all real whitespace are stripped before a lenient MIME Base64 decode.
     */
    private fun decodePem(raw: String): ByteArray {
        val cleaned = raw
            .trim()
            .removeSurrounding("\"")
            .replace("\\n", "")
            .replace("\\r", "")
            .replace(Regex("-----BEGIN [^-]+-----"), "")
            .replace(Regex("-----END [^-]+-----"), "")
            .replace(Regex("\\s"), "")
        return Base64.getMimeDecoder().decode(cleaned)
    }

    data class Claims(val userId: UUID, val email: String, val name: String, val role: UserRole)

    fun issueAccessToken(claims: Claims): String {
        val now = Instant.now()
        return JWT.create()
            .withIssuer("takeoff")
            .withSubject(claims.userId.toString())
            .withClaim("email", claims.email)
            .withClaim("name", claims.name)
            .withClaim("role", claims.role.name)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(now.plusSeconds(props.accessTtlSeconds)))
            .sign(algorithm)
    }

    fun issueRefreshToken(): String = UUID.randomUUID().toString() + UUID.randomUUID().toString()

    fun verify(token: String): Claims {
        val decoded = JWT.require(algorithm).withIssuer("takeoff").build().verify(token)
        return Claims(
            userId = UUID.fromString(decoded.subject),
            email = decoded.getClaim("email").asString(),
            name = decoded.getClaim("name").asString(),
            role = UserRole.valueOf(decoded.getClaim("role").asString()),
        )
    }

    data class AdminClaims(val adminId: UUID, val email: String, val name: String, val role: AdminRole)

    fun issueAdminAccessToken(claims: AdminClaims): String {
        val now = Instant.now()
        return JWT.create()
            .withIssuer("takeoff-admin")
            .withSubject(claims.adminId.toString())
            .withClaim("email", claims.email)
            .withClaim("name", claims.name)
            .withClaim("role", claims.role.name)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(now.plusSeconds(props.adminAccessTtlSeconds)))
            .sign(algorithm)
    }

    fun verifyAdmin(token: String): AdminClaims {
        val decoded = JWT.require(algorithm).withIssuer("takeoff-admin").build().verify(token)
        return AdminClaims(
            adminId = UUID.fromString(decoded.subject),
            email = decoded.getClaim("email").asString(),
            name = decoded.getClaim("name").asString(),
            role = AdminRole.valueOf(decoded.getClaim("role").asString()),
        )
    }
}

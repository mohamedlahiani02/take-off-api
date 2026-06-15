package tn.takeoff.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.springframework.stereotype.Service
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

        val privateKey = run {
            val pem = Files.readString(Paths.get(props.privateKeyPath))
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\s".toRegex(), "")
            val bytes = Base64.getDecoder().decode(pem)
            kf.generatePrivate(PKCS8EncodedKeySpec(bytes)) as RSAPrivateKey
        }

        val publicKey = run {
            val pem = Files.readString(Paths.get(props.publicKeyPath))
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s".toRegex(), "")
            val bytes = Base64.getDecoder().decode(pem)
            kf.generatePublic(X509EncodedKeySpec(bytes)) as RSAPublicKey
        }

        Algorithm.RSA256(publicKey, privateKey)
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
}

package tn.takeoff.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "takeoff.jwt")
data class JwtProperties(
    val privateKeyPath: String,
    val publicKeyPath: String,
    val accessTtlSeconds: Long,
    val refreshTtlSeconds: Long,
)

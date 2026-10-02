package com.anksoft.kmpdemo.server.auth.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.anksoft.kmpdemo.server.auth.domain.AccessTokenIssuer
import java.time.Duration
import java.time.Instant
import java.util.UUID

class JwtConfig(
    secret: String,
    val issuer: String,
    val audience: String,
    val accessTtl: Duration,
) {
    val algorithm: Algorithm = Algorithm.HMAC256(secret)

    /** Verifier with a 5 second clock skew tolerance. */
    fun verifier(): JWTVerifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .withAudience(audience)
        .acceptLeeway(CLOCK_SKEW_SECONDS)
        .build()

    private companion object {
        const val CLOCK_SKEW_SECONDS = 5L
    }
}

class JwtAccessTokenIssuer(private val config: JwtConfig) : AccessTokenIssuer {
    override fun issue(userId: UUID, now: Instant): String = JWT.create()
        .withIssuer(config.issuer)
        .withAudience(config.audience)
        .withSubject(userId.toString())
        // Unique id so two tokens issued within the same second still differ.
        .withJWTId(UUID.randomUUID().toString())
        .withIssuedAt(now)
        .withExpiresAt(now.plus(config.accessTtl))
        .sign(config.algorithm)
}

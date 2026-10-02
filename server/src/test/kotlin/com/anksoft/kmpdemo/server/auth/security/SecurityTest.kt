package com.anksoft.kmpdemo.server.auth.security

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import assertk.assertions.startsWith
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith

class Argon2PasswordHasherTest {
    private val hasher = Argon2PasswordHasher()

    @Test
    fun `hash is an argon2id PHC string that does not contain the password`() = runTest { // AC-11
        val hash = hasher.hash("Password1")

        assertThat(hash).startsWith("\$argon2id\$v=19\$m=19456,t=2,p=1\$")
        assertThat(hash).doesNotContain("Password1")
    }

    @Test
    fun `verify accepts the right password and rejects a wrong one`() = runTest { // AC-4, AC-5
        val hash = hasher.hash("Password1")

        assertThat(hasher.verify("Password1", hash)).isTrue()
        assertThat(hasher.verify("Password2", hash)).isFalse()
    }

    @Test
    fun `hashing the same password twice yields different salts`() = runTest {
        assertThat(hasher.hash("Password1")).isNotEqualTo(hasher.hash("Password1"))
    }
}

class JwtAccessTokenIssuerTest {
    private val secret = "0123456789abcdef0123456789abcdef"
    private val config = JwtConfig(secret, "iss", "aud", Duration.ofMinutes(15))
    private val issuer = JwtAccessTokenIssuer(config)

    @Test
    fun `issued token carries the expected claims`() { // AC-8
        val userId = UUID.randomUUID()
        val now = Instant.now()

        val decoded = config.verifier().verify(issuer.issue(userId, now))

        assertThat(decoded.subject).isEqualTo(userId.toString())
        assertThat(decoded.issuer).isEqualTo("iss")
        assertThat(decoded.audience).isEqualTo(listOf("aud"))
        assertThat(decoded.expiresAtAsInstant.epochSecond).isEqualTo(now.plus(Duration.ofMinutes(15)).epochSecond)
    }

    @Test
    fun `tokens issued in the same instant differ`() { // AC-4
        val now = Instant.now()
        assertThat(issuer.issue(UUID.randomUUID(), now)).isNotEqualTo(issuer.issue(UUID.randomUUID(), now))
    }

    @Test
    fun `expired token is rejected beyond the clock skew`() { // AC-9
        val token = issuer.issue(UUID.randomUUID(), Instant.now().minus(Duration.ofMinutes(16)))
        assertFailsWith<JWTVerificationException> { config.verifier().verify(token) }
    }

    @Test
    fun `token signed with another secret is rejected`() { // AC-9
        val forged = JWT.create().withIssuer("iss").withAudience("aud").withSubject("x")
            .withExpiresAt(Instant.now().plusSeconds(60)).sign(Algorithm.HMAC256("another-secret-another-secret-123456"))
        assertFailsWith<JWTVerificationException> { config.verifier().verify(forged) }
    }

    @Test
    fun `token for another audience is rejected`() { // AC-9
        val other = JwtAccessTokenIssuer(JwtConfig(secret, "iss", "other", Duration.ofMinutes(15)))
        assertFailsWith<JWTVerificationException> {
            config.verifier().verify(other.issue(UUID.randomUUID(), Instant.now()))
        }
    }
}

class SecureRefreshTokenGeneratorTest {
    private val generator = SecureRefreshTokenGenerator()

    @Test
    fun `generated token is 43 url safe characters and unique`() { // AC-6
        val a = generator.generate()
        val b = generator.generate()

        assertThat(a.length).isEqualTo(43)
        assertThat(a.matches(Regex("^[A-Za-z0-9_-]+$"))).isTrue()
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `hash is lowercase sha256 hex and deterministic`() { // AC-11
        val hash = generator.hash("token")

        assertThat(hash.length).isEqualTo(64)
        assertThat(hash).isEqualTo(generator.hash("token"))
        assertThat(hash).isEqualTo("3c469e9d6c5875d37a43f353d4f88e61fcf812c66eee3457465a40b0da4153e0")
        assertThat(hash).doesNotContain("token")
    }
}

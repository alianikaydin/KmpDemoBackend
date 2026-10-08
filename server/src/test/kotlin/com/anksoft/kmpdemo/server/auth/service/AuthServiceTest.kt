package com.anksoft.kmpdemo.server.auth.service

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.anksoft.kmpdemo.server.auth.domain.AuthError
import com.anksoft.kmpdemo.server.auth.domain.AuthResult
import com.anksoft.kmpdemo.server.auth.domain.AuthSession
import com.anksoft.kmpdemo.server.auth.fakes.FakeAccessTokenIssuer
import com.anksoft.kmpdemo.server.auth.fakes.FakePasswordHasher
import com.anksoft.kmpdemo.server.auth.fakes.FakeRefreshTokenRepository
import com.anksoft.kmpdemo.server.auth.fakes.FakeUserRepository
import com.anksoft.kmpdemo.server.auth.security.SecureRefreshTokenGenerator
import com.anksoft.kmpdemo.server.consent.domain.AccountConsent
import com.anksoft.kmpdemo.server.consent.domain.ConsentDecisionInput
import com.anksoft.kmpdemo.server.consent.domain.ConsentDecisionStatus
import com.anksoft.kmpdemo.server.consent.domain.ConsentSource
import com.anksoft.kmpdemo.server.consent.domain.NewConsentDecision
import com.anksoft.kmpdemo.server.consent.fakes.FakeConsentDecisionRepository
import com.anksoft.kmpdemo.server.consent.fakes.FakeConsentTextRepository
import com.anksoft.kmpdemo.server.consent.service.ConsentService
import com.anksoft.kmpdemo.server.support.MutableClock
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.util.UUID
import kotlin.test.Test

class AuthServiceTest {

    private val consentDecisions = FakeConsentDecisionRepository()
    private val consentTexts = FakeConsentTextRepository().apply { publish(1) }
    private val users = FakeUserRepository(consentDecisions)
    private val tokens = FakeRefreshTokenRepository()
    private val hasher = FakePasswordHasher()
    private val clock = MutableClock()
    private val consents = ConsentService(consentTexts, consentDecisions, clock)
    private val service = AuthService(
        users, tokens, hasher, FakeAccessTokenIssuer(), SecureRefreshTokenGenerator(), clock, Duration.ofDays(30),
        consents,
    )

    private fun AuthResult<AuthSession>.ok(): AuthSession =
        (this as? AuthResult.Ok)?.value ?: error("expected Ok but was $this")

    private fun AuthResult<*>.errorOrNull(): AuthError? = (this as? AuthResult.Err)?.error

    @Test
    fun `register creates a user with normalized email and hashed password`() = runTest { // AC-1, AC-11
        val session = service.register(" Ali@Example.COM ", "Password1").ok()

        assertThat(session.user.email).isEqualTo("ali@example.com")
        assertThat(session.refreshToken.length).isEqualTo(43)
        assertThat(users.stored["ali@example.com"]?.passwordHash).isEqualTo("hashed:Password1")
        assertThat(tokens.records).hasSize(1)
    }

    @Test
    fun `register stores only the hash of the refresh token`() = runTest { // AC-11
        val session = service.register("a@b.com", "Password1").ok()

        assertThat(tokens.records[session.refreshToken]).isEqualTo(null)
        assertThat(tokens.records.values.single().tokenHash).isNotEqualTo(session.refreshToken)
    }

    @Test
    fun `register rejects duplicate email regardless of case and whitespace`() = runTest { // AC-2
        service.register("ali@example.com", "Password1").ok()

        val result = service.register("  ALI@Example.com ", "Password1")

        assertThat(result.errorOrNull()).isEqualTo(AuthError.EMAIL_TAKEN)
        assertThat(users.stored).hasSize(1)
    }

    @Test
    fun `register maps a lost unique race to email taken`() = runTest { // AC-2
        val racing = object : com.anksoft.kmpdemo.server.auth.repository.UserRepository by users {
            override suspend fun findByEmail(email: String) = null
            override suspend fun create(
                user: com.anksoft.kmpdemo.server.auth.domain.NewUser,
                initialConsent: NewConsentDecision?,
            ) = null
        }
        val racingService = AuthService(
            racing, tokens, hasher, FakeAccessTokenIssuer(), SecureRefreshTokenGenerator(), clock, Duration.ofDays(30),
            consents,
        )

        assertThat(racingService.register("a@b.com", "Password1").errorOrNull()).isEqualTo(AuthError.EMAIL_TAKEN)
    }

    @Test
    fun `register rejects invalid input without creating anything`() = runTest { // AC-3
        listOf(
            "" to "Password1",
            "not-an-email" to "Password1",
            "a@b.com" to "short1A",
            "a@b.com" to "nodigitsHere",
            "a@b.com" to "nouppercase1",
            "a@b.com" to "A1" + "a".repeat(128),
            ("a".repeat(250) + "@b.com") to "Password1",
        ).forEach { (email, password) ->
            assertThat(service.register(email, password).errorOrNull(), name = "$email/$password")
                .isEqualTo(AuthError.INVALID_INPUT)
        }
        assertThat(users.stored).hasSize(0)
        assertThat(hasher.hashCalls.get()).isEqualTo(0)
    }

    @Test
    fun `register without consent returns a none consent and stores no decision`() = runTest { // AC-4
        val session = service.register("a@b.com", "Password1").ok()

        assertThat(session.consent).isEqualTo(AccountConsent.NONE)
        assertThat(consentDecisions.rows).hasSize(0)
    }

    @Test
    fun `register with consent stores the decision and returns it`() = runTest { // AC-2, AC-3
        val session = service.register("a@b.com", "Password1", ConsentDecisionInput("granted", 1, "tr")).ok()

        assertThat(session.consent?.status).isEqualTo(ConsentDecisionStatus.GRANTED)
        assertThat(session.consent?.textVersion).isEqualTo(1)
        assertThat(session.consent?.textLanguage).isEqualTo("tr")
        assertThat(consentDecisions.rows).hasSize(1)
        assertThat(consentDecisions.rows.single().second.source).isEqualTo(ConsentSource.REGISTER)
    }

    @Test
    fun `register with a denied decision stores it`() = runTest { // AC-2
        val session = service.register("a@b.com", "Password1", ConsentDecisionInput("denied", 1, "en")).ok()

        assertThat(session.consent?.status).isEqualTo(ConsentDecisionStatus.DENIED)
        assertThat(consentDecisions.rows).hasSize(1)
    }

    @Test
    fun `register with an unknown consent version creates no account and skips hashing`() = runTest { // AC-5
        val result = service.register("a@b.com", "Password1", ConsentDecisionInput("granted", 999, "tr"))

        assertThat(result.errorOrNull()).isEqualTo(AuthError.UNKNOWN_CONSENT_VERSION)
        assertThat(users.stored).hasSize(0)
        assertThat(hasher.hashCalls.get()).isEqualTo(0)
        assertThat(consentDecisions.rows).hasSize(0)
    }

    @Test
    fun `register with an invalid consent status creates no account and skips hashing`() = runTest { // AC-5
        val result = service.register("a@b.com", "Password1", ConsentDecisionInput("maybe", 1, "tr"))

        assertThat(result.errorOrNull()).isEqualTo(AuthError.INVALID_INPUT)
        assertThat(users.stored).hasSize(0)
        assertThat(hasher.hashCalls.get()).isEqualTo(0)
    }

    @Test
    fun `register stores no decision when the email is taken`() = runTest { // AC-5
        service.register("a@b.com", "Password1").ok()

        val result = service.register("a@b.com", "Password1", ConsentDecisionInput("granted", 1, "tr"))

        assertThat(result.errorOrNull()).isEqualTo(AuthError.EMAIL_TAKEN)
        assertThat(consentDecisions.rows).hasSize(0)
    }

    @Test
    fun `login and refresh leave the consent out of the session`() = runTest { // AC-17
        val registered = service.register("a@b.com", "Password1", ConsentDecisionInput("granted", 1, "tr")).ok()

        assertThat(service.login("a@b.com", "Password1").ok().consent).isNull()
        assertThat(service.refresh(registered.refreshToken).ok().consent).isNull()
    }

    @Test
    fun `login with valid credentials returns a new token pair`() = runTest { // AC-4
        val registered = service.register("a@b.com", "Password1").ok()

        val session = service.login(" A@B.com ", "Password1").ok()

        assertThat(session.user).isEqualTo(registered.user)
        assertThat(session.refreshToken).isNotEqualTo(registered.refreshToken)
        assertThat(session.accessToken).isNotEqualTo(registered.accessToken)
    }

    @Test
    fun `login with wrong password returns invalid credentials`() = runTest { // AC-5
        service.register("a@b.com", "Password1").ok()

        assertThat(service.login("a@b.com", "Wrong1234").errorOrNull()).isEqualTo(AuthError.INVALID_CREDENTIALS)
    }

    @Test
    fun `login with unknown email returns the same error as a wrong password`() = runTest { // AC-5
        assertThat(service.login("nobody@b.com", "Password1").errorOrNull()).isEqualTo(AuthError.INVALID_CREDENTIALS)
    }

    @Test
    fun `login unknown email still runs verify`() = runTest { // AC-5
        service.warmUp()

        service.login("nobody@b.com", "Password1")

        assertThat(hasher.verifyCalls.get()).isEqualTo(1)
    }

    @Test
    fun `login rejects empty and oversized input`() = runTest { // AC-3
        assertThat(service.login("", "Password1").errorOrNull()).isEqualTo(AuthError.INVALID_INPUT)
        assertThat(service.login("a@b.com", "").errorOrNull()).isEqualTo(AuthError.INVALID_INPUT)
        assertThat(service.login("a@b.com", "x".repeat(129)).errorOrNull()).isEqualTo(AuthError.INVALID_INPUT)
    }

    @Test
    fun `refresh rotates the token and keeps the family`() = runTest { // AC-6
        val first = service.register("a@b.com", "Password1").ok()

        val second = service.refresh(first.refreshToken).ok()
        val third = service.refresh(second.refreshToken).ok()

        assertThat(second.refreshToken).isNotEqualTo(first.refreshToken)
        assertThat(third.user).isEqualTo(first.user)
        assertThat(tokens.records.values.map { it.familyId }.toSet()).hasSize(1)
    }

    @Test
    fun `refresh rejects unknown token`() = runTest { // AC-7
        assertThat(service.refresh("unknown-token").errorOrNull()).isEqualTo(AuthError.INVALID_TOKEN)
    }

    @Test
    fun `refresh rejects expired token`() = runTest { // AC-7
        val session = service.register("a@b.com", "Password1").ok()
        clock.advance(Duration.ofDays(31))

        assertThat(service.refresh(session.refreshToken).errorOrNull()).isEqualTo(AuthError.INVALID_TOKEN)
    }

    @Test
    fun `refresh rejects revoked token after logout`() = runTest { // AC-7
        val session = service.register("a@b.com", "Password1").ok()
        service.logout(session.refreshToken)

        assertThat(service.refresh(session.refreshToken).errorOrNull()).isEqualTo(AuthError.INVALID_TOKEN)
    }

    @Test
    fun `reusing a consumed refresh token revokes the whole family`() = runTest { // AC-7
        val first = service.register("a@b.com", "Password1").ok()
        val second = service.refresh(first.refreshToken).ok()

        assertThat(service.refresh(first.refreshToken).errorOrNull()).isEqualTo(AuthError.INVALID_TOKEN)

        assertThat(service.refresh(second.refreshToken).errorOrNull()).isEqualTo(AuthError.INVALID_TOKEN)
    }

    @Test
    fun `refresh rejects empty and oversized token as invalid input`() = runTest { // AC-3
        assertThat(service.refresh("").errorOrNull()).isEqualTo(AuthError.INVALID_INPUT)
        assertThat(service.refresh("x".repeat(257)).errorOrNull()).isEqualTo(AuthError.INVALID_INPUT)
    }

    @Test
    fun `refresh for a deleted user is rejected`() = runTest { // AC-7
        val session = service.register("a@b.com", "Password1").ok()
        users.stored.clear()

        assertThat(service.refresh(session.refreshToken).errorOrNull()).isEqualTo(AuthError.INVALID_TOKEN)
    }

    @Test
    fun `logout is idempotent for unknown and revoked tokens`() = runTest { // AC-7
        val session = service.register("a@b.com", "Password1").ok()

        assertThat(service.logout(session.refreshToken)).isInstanceOf(AuthResult.Ok::class)
        assertThat(service.logout(session.refreshToken)).isInstanceOf(AuthResult.Ok::class)
        assertThat(service.logout("never-issued")).isInstanceOf(AuthResult.Ok::class)
    }

    @Test
    fun `logout revokes sibling tokens of the family`() = runTest { // AC-7
        val first = service.register("a@b.com", "Password1").ok()
        val second = service.refresh(first.refreshToken).ok()

        service.logout(second.refreshToken)

        assertThat(tokens.records.values.all { it.revokedAt != null }).isEqualTo(true)
    }

    @Test
    fun `currentUser returns the stored user`() = runTest { // AC-8
        val session = service.register("a@b.com", "Password1").ok()

        val result = service.currentUser(session.user.id)

        assertThat((result as AuthResult.Ok).value).isEqualTo(session.user)
    }

    @Test
    fun `currentUser for a missing user is rejected`() = runTest { // AC-9
        assertThat(service.currentUser(UUID.randomUUID()).errorOrNull()).isEqualTo(AuthError.INVALID_TOKEN)
    }

    @Test
    fun `refresh token expiry is set from the configured ttl`() = runTest {
        service.register("a@b.com", "Password1").ok()

        val record = tokens.records.values.single()
        assertThat(record.expiresAt).isNotNull()
        assertThat(Duration.between(record.createdAt, record.expiresAt)).isEqualTo(Duration.ofDays(30))
    }
}

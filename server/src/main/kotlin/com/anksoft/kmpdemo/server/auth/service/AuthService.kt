package com.anksoft.kmpdemo.server.auth.service

import com.anksoft.kmpdemo.contract.auth.CredentialRules
import com.anksoft.kmpdemo.server.auth.domain.AccessTokenIssuer
import com.anksoft.kmpdemo.server.auth.domain.AuthError
import com.anksoft.kmpdemo.server.auth.domain.AuthResult
import com.anksoft.kmpdemo.server.auth.domain.AuthSession
import com.anksoft.kmpdemo.server.auth.domain.NewUser
import com.anksoft.kmpdemo.server.auth.domain.PasswordHasher
import com.anksoft.kmpdemo.server.auth.domain.RefreshTokenGenerator
import com.anksoft.kmpdemo.server.auth.domain.RefreshTokenRecord
import com.anksoft.kmpdemo.server.auth.domain.User
import com.anksoft.kmpdemo.server.auth.repository.RefreshTokenRepository
import com.anksoft.kmpdemo.server.auth.repository.UserRepository
import com.anksoft.kmpdemo.server.consent.domain.AccountConsent
import com.anksoft.kmpdemo.server.consent.domain.ConsentDecisionInput
import com.anksoft.kmpdemo.server.consent.domain.ConsentError
import com.anksoft.kmpdemo.server.consent.domain.ConsentResult
import com.anksoft.kmpdemo.server.consent.domain.ConsentSource
import com.anksoft.kmpdemo.server.consent.service.ConsentService
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.util.UUID

/**
 * Auth business rules. Knows nothing about HTTP or the database: it talks to
 * repositories and security ports and returns [AuthResult]s that the routes
 * translate into status codes.
 */
class AuthService(
    private val users: UserRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val hasher: PasswordHasher,
    private val accessTokens: AccessTokenIssuer,
    private val refreshGenerator: RefreshTokenGenerator,
    private val clock: Clock,
    private val refreshTtl: Duration,
    private val consents: ConsentService,
) {
    private val log = LoggerFactory.getLogger(AuthService::class.java)

    @Volatile
    private var dummyHash: String? = null

    /** Precomputes the hash used to equalize login timing for unknown e-mails (AC-5). */
    suspend fun warmUp() {
        dummyHash = hasher.hash(UUID.randomUUID().toString())
    }

    /**
     * Creates the account and, when [consent] is given, its first consent decision in the same
     * transaction. An invalid decision rejects the whole registration before any hashing or writing.
     */
    suspend fun register(
        rawEmail: String,
        password: String,
        consent: ConsentDecisionInput? = null,
    ): AuthResult<AuthSession> {
        val email = CredentialRules.normalizeEmail(rawEmail)
        if (email.length > CredentialRules.MAX_EMAIL_LENGTH ||
            !CredentialRules.isValidEmail(email) ||
            CredentialRules.passwordViolations(password).isNotEmpty()
        ) {
            return AuthResult.Err(AuthError.INVALID_INPUT)
        }
        val prepared = if (consent == null) {
            null
        } else {
            when (val result = consents.prepareDecision(consent, ConsentSource.REGISTER)) {
                is ConsentResult.Ok -> result.value
                is ConsentResult.Err -> return AuthResult.Err(
                    when (result.error) {
                        ConsentError.UNKNOWN_TEXT_VERSION -> AuthError.UNKNOWN_CONSENT_VERSION
                        // prepareDecision never reports ACCOUNT_NOT_FOUND; there is no account yet.
                        ConsentError.INVALID_INPUT, ConsentError.ACCOUNT_NOT_FOUND -> AuthError.INVALID_INPUT
                    },
                )
            }
        }
        if (users.findByEmail(email) != null) return AuthResult.Err(AuthError.EMAIL_TAKEN)

        val created = users.create(NewUser(UUID.randomUUID(), email, hasher.hash(password)), prepared)
            ?: return AuthResult.Err(AuthError.EMAIL_TAKEN)
        val accountConsent = prepared?.let { consents.describe(it) } ?: AccountConsent.NONE
        return AuthResult.Ok(newSession(created, UUID.randomUUID()).copy(consent = accountConsent))
    }

    suspend fun login(rawEmail: String, password: String): AuthResult<AuthSession> {
        val email = CredentialRules.normalizeEmail(rawEmail)
        if (email.isEmpty() || email.length > CredentialRules.MAX_EMAIL_LENGTH ||
            password.isEmpty() || password.length > CredentialRules.MAX_PASSWORD_LENGTH
        ) {
            return AuthResult.Err(AuthError.INVALID_INPUT)
        }
        val credentials = users.findByEmail(email)
        if (credentials == null) {
            // Same work as a real verification so unknown e-mails are not distinguishable by timing.
            hasher.verify(password, dummyHash ?: hasher.hash(UUID.randomUUID().toString()).also { dummyHash = it })
            return AuthResult.Err(AuthError.INVALID_CREDENTIALS)
        }
        if (!hasher.verify(password, credentials.passwordHash)) {
            return AuthResult.Err(AuthError.INVALID_CREDENTIALS)
        }
        return AuthResult.Ok(newSession(credentials.user, UUID.randomUUID()))
    }

    suspend fun refresh(refreshToken: String): AuthResult<AuthSession> {
        if (!isPlausibleToken(refreshToken)) return AuthResult.Err(AuthError.INVALID_INPUT)
        val now = clock.instant()
        val hash = refreshGenerator.hash(refreshToken)

        val refreshToken = refreshGenerator.generate()
        // userId/familyId are placeholders: the repository takes them from the consumed token.
        val successor = newRecord(UUID.randomUUID(), UUID.randomUUID(), refreshToken, now)
        val consumed = refreshTokens.rotate(hash, now, successor)
        if (consumed == null) {
            val existing = refreshTokens.findByHash(hash)
            if (existing?.usedAt != null) {
                // A consumed token came back: assume theft and revoke the whole family.
                refreshTokens.revokeFamily(existing.familyId, now)
                log.warn("Refresh token reuse detected: userId={} familyId={}", existing.userId, existing.familyId)
            }
            return AuthResult.Err(AuthError.INVALID_TOKEN)
        }
        val user = users.findById(consumed.userId) ?: return AuthResult.Err(AuthError.INVALID_TOKEN)
        return AuthResult.Ok(AuthSession(accessTokens.issue(user.id, now), refreshToken, user))
    }

    /** Idempotent: unknown or already revoked tokens succeed too. */
    suspend fun logout(refreshToken: String): AuthResult<Unit> {
        if (!isPlausibleToken(refreshToken)) return AuthResult.Err(AuthError.INVALID_INPUT)
        refreshTokens.findByHash(refreshGenerator.hash(refreshToken))
            ?.let { refreshTokens.revokeFamily(it.familyId, clock.instant()) }
        return AuthResult.Ok(Unit)
    }

    suspend fun currentUser(id: UUID): AuthResult<User> =
        users.findById(id)?.let { AuthResult.Ok(it) } ?: AuthResult.Err(AuthError.INVALID_TOKEN)

    private suspend fun newSession(user: User, familyId: UUID): AuthSession {
        val now = clock.instant()
        val refreshToken = refreshGenerator.generate()
        refreshTokens.create(newRecord(user.id, familyId, refreshToken, now))
        return AuthSession(accessTokens.issue(user.id, now), refreshToken, user)
    }

    private fun newRecord(userId: UUID, familyId: UUID, refreshToken: String, now: java.time.Instant) =
        RefreshTokenRecord(
            id = UUID.randomUUID(),
            userId = userId,
            familyId = familyId,
            tokenHash = refreshGenerator.hash(refreshToken),
            createdAt = now,
            expiresAt = now.plus(refreshTtl),
            usedAt = null,
            revokedAt = null,
        )

    private fun isPlausibleToken(token: String) = token.isNotEmpty() && token.length <= MAX_TOKEN_LENGTH

    private companion object {
        const val MAX_TOKEN_LENGTH = 256
    }
}

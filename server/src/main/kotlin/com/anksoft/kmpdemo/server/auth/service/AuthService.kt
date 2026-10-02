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
) {
    private val log = LoggerFactory.getLogger(AuthService::class.java)

    @Volatile
    private var dummyHash: String? = null

    /** Precomputes the hash used to equalize login timing for unknown e-mails (AC-5). */
    suspend fun warmUp() {
        dummyHash = hasher.hash(UUID.randomUUID().toString())
    }

    suspend fun register(rawEmail: String, password: String): AuthResult<AuthSession> {
        val email = CredentialRules.normalizeEmail(rawEmail)
        if (email.length > CredentialRules.MAX_EMAIL_LENGTH ||
            !CredentialRules.isValidEmail(email) ||
            CredentialRules.passwordViolations(password).isNotEmpty()
        ) {
            return AuthResult.Err(AuthError.INVALID_INPUT)
        }
        if (users.findByEmail(email) != null) return AuthResult.Err(AuthError.EMAIL_TAKEN)

        val created = users.create(NewUser(UUID.randomUUID(), email, hasher.hash(password)))
            ?: return AuthResult.Err(AuthError.EMAIL_TAKEN)
        return AuthResult.Ok(newSession(created, UUID.randomUUID()))
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

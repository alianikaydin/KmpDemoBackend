package com.anksoft.kmpdemo.server.auth.fakes

import com.anksoft.kmpdemo.server.auth.domain.AccessTokenIssuer
import com.anksoft.kmpdemo.server.auth.domain.NewUser
import com.anksoft.kmpdemo.server.auth.domain.PasswordHasher
import com.anksoft.kmpdemo.server.auth.domain.RefreshTokenRecord
import com.anksoft.kmpdemo.server.auth.domain.User
import com.anksoft.kmpdemo.server.auth.domain.UserCredentials
import com.anksoft.kmpdemo.server.auth.repository.RefreshTokenRepository
import com.anksoft.kmpdemo.server.auth.repository.UserRepository
import com.anksoft.kmpdemo.server.consent.domain.NewConsentDecision
import com.anksoft.kmpdemo.server.consent.fakes.FakeConsentDecisionRepository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class FakePasswordHasher : PasswordHasher {
    val hashCalls = AtomicInteger()
    val verifyCalls = AtomicInteger()

    override suspend fun hash(raw: String): String {
        hashCalls.incrementAndGet()
        return "hashed:$raw"
    }

    override suspend fun verify(raw: String, hash: String): Boolean {
        verifyCalls.incrementAndGet()
        return hash == "hashed:$raw"
    }
}

class FakeAccessTokenIssuer : AccessTokenIssuer {
    private val counter = AtomicInteger()
    override fun issue(userId: UUID, now: Instant): String = "access:$userId:${counter.incrementAndGet()}"
}

class FakeUserRepository(private val consentDecisions: FakeConsentDecisionRepository? = null) : UserRepository {
    val stored = ConcurrentHashMap<String, UserCredentials>()

    override suspend fun findByEmail(email: String): UserCredentials? = stored[email]

    override suspend fun findById(id: UUID): User? = stored.values.firstOrNull { it.user.id == id }?.user

    override suspend fun create(user: NewUser, initialConsent: NewConsentDecision?): User? {
        val created = User(user.id, user.email, null)
        if (stored.putIfAbsent(user.email, UserCredentials(created, user.passwordHash)) != null) return null
        if (initialConsent != null) consentDecisions?.add(user.id, initialConsent)
        return created
    }
}

class FakeRefreshTokenRepository : RefreshTokenRepository {
    private val lock = Any()
    val records = ConcurrentHashMap<String, RefreshTokenRecord>()

    override suspend fun create(record: RefreshTokenRecord) {
        records[record.tokenHash] = record
    }

    override suspend fun markUsedIfActive(tokenHash: String, now: Instant): RefreshTokenRecord? {
        synchronized(lock) {
            val record = records[tokenHash] ?: return null
            if (record.usedAt != null || record.revokedAt != null || !record.expiresAt.isAfter(now)) return null
            records[tokenHash] = record.copy(usedAt = now)
            return record
        }
    }

    override suspend fun rotate(oldHash: String, now: Instant, newRecord: RefreshTokenRecord): RefreshTokenRecord? {
        synchronized(lock) {
            val consumed = records[oldHash] ?: return null
            if (consumed.usedAt != null || consumed.revokedAt != null || !consumed.expiresAt.isAfter(now)) return null
            records[oldHash] = consumed.copy(usedAt = now)
            records[newRecord.tokenHash] = newRecord.copy(userId = consumed.userId, familyId = consumed.familyId)
            return consumed
        }
    }

    override suspend fun findByHash(tokenHash: String): RefreshTokenRecord? = records[tokenHash]

    override suspend fun revokeFamily(familyId: UUID, now: Instant): Int {
        synchronized(lock) {
            var count = 0
            records.replaceAll { _, r ->
                if (r.familyId == familyId && r.revokedAt == null) {
                    count++
                    r.copy(revokedAt = now)
                } else {
                    r
                }
            }
            return count
        }
    }
}

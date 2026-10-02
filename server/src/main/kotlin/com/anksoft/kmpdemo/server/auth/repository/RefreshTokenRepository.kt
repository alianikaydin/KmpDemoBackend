package com.anksoft.kmpdemo.server.auth.repository

import com.anksoft.kmpdemo.server.auth.domain.RefreshTokenRecord
import java.time.Instant
import java.util.UUID

interface RefreshTokenRepository {
    suspend fun create(record: RefreshTokenRecord)

    /**
     * Atomically consumes the token if it is unused, not revoked and not expired at [now];
     * returns the consumed record, or null when nothing matched.
     */
    suspend fun markUsedIfActive(tokenHash: String, now: Instant): RefreshTokenRecord?

    /**
     * Consumes the token like [markUsedIfActive] and inserts [newRecord] in the same transaction,
     * so a concurrent loser that then revokes the family is guaranteed to see the new token.
     * The inserted row inherits `userId` and `familyId` from the consumed record; those fields of
     * [newRecord] are ignored. Returns the consumed record, or null (nothing inserted) when no
     * active token matched.
     */
    suspend fun rotate(oldHash: String, now: Instant, newRecord: RefreshTokenRecord): RefreshTokenRecord?

    suspend fun findByHash(tokenHash: String): RefreshTokenRecord?

    /** Revokes every not yet revoked token of the family; returns how many were revoked. */
    suspend fun revokeFamily(familyId: UUID, now: Instant): Int
}

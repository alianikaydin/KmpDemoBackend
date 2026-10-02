package com.anksoft.kmpdemo.server.auth.domain

import java.time.Instant
import java.util.UUID

/** Stored form of a refresh token; only the SHA-256 [tokenHash] is persisted, never the token. */
data class RefreshTokenRecord(
    val id: UUID,
    val userId: UUID,
    val familyId: UUID,
    val tokenHash: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val usedAt: Instant?,
    val revokedAt: Instant?,
)

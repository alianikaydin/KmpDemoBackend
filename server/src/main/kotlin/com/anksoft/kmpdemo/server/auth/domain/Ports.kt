package com.anksoft.kmpdemo.server.auth.domain

import java.time.Instant
import java.util.UUID

interface PasswordHasher {
    suspend fun hash(raw: String): String
    suspend fun verify(raw: String, hash: String): Boolean
}

interface AccessTokenIssuer {
    fun issue(userId: UUID, now: Instant): String
}

interface RefreshTokenGenerator {
    fun generate(): String
    fun hash(token: String): String
}

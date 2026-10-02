package com.anksoft.kmpdemo.server.auth.data

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/** Mirrors `V1__create_users.sql`; the schema itself is owned by Flyway, not by Exposed. */
object UsersTable : Table("users") {
    val id = javaUUID("id")
    val email = varchar("email", 254)
    val passwordHash = varchar("password_hash", 255)
    val name = varchar("name", 100).nullable()
    override val primaryKey = PrimaryKey(id)
}

/** Mirrors `V2__create_refresh_tokens.sql`. */
object RefreshTokensTable : Table("refresh_tokens") {
    val id = javaUUID("id")
    val userId = javaUUID("user_id")
    val familyId = javaUUID("family_id")
    val tokenHash = varchar("token_hash", 64)
    val createdAt = timestampWithTimeZone("created_at")
    val expiresAt = timestampWithTimeZone("expires_at")
    val usedAt = timestampWithTimeZone("used_at").nullable()
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

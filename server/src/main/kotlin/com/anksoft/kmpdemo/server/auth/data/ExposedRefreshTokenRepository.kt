package com.anksoft.kmpdemo.server.auth.data

import com.anksoft.kmpdemo.server.auth.domain.RefreshTokenRecord
import com.anksoft.kmpdemo.server.auth.repository.RefreshTokenRepository
import com.anksoft.kmpdemo.server.db.io
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.updateReturning
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class ExposedRefreshTokenRepository(private val db: Database) : RefreshTokenRepository {

    override suspend fun create(record: RefreshTokenRecord) {
        db.io { insertRecord(record) }
    }

    /** One `UPDATE ... RETURNING` statement, so of two concurrent callers exactly one wins. */
    override suspend fun markUsedIfActive(tokenHash: String, now: Instant): RefreshTokenRecord? =
        db.io { consume(tokenHash, now) }

    /**
     * The consuming UPDATE and the INSERT of the successor share one transaction. A concurrent
     * loser's UPDATE blocks on the row lock until this commits, so its follow-up family
     * revocation always sees (and revokes) the successor.
     */
    override suspend fun rotate(oldHash: String, now: Instant, newRecord: RefreshTokenRecord): RefreshTokenRecord? =
        db.io {
            consume(oldHash, now)?.also { consumed ->
                insertRecord(newRecord.copy(userId = consumed.userId, familyId = consumed.familyId))
            }
        }

    private fun insertRecord(record: RefreshTokenRecord) {
        RefreshTokensTable.insert {
            it[id] = record.id
            it[userId] = record.userId
            it[familyId] = record.familyId
            it[tokenHash] = record.tokenHash
            it[createdAt] = record.createdAt.utc()
            it[expiresAt] = record.expiresAt.utc()
            it[usedAt] = record.usedAt?.utc()
            it[revokedAt] = record.revokedAt?.utc()
        }
    }

    private fun consume(tokenHash: String, now: Instant): RefreshTokenRecord? = RefreshTokensTable
        .updateReturning(
            where = {
                (RefreshTokensTable.tokenHash eq tokenHash) and
                    RefreshTokensTable.usedAt.isNull() and
                    RefreshTokensTable.revokedAt.isNull() and
                    (RefreshTokensTable.expiresAt greater now.utc())
            },
        ) { it[usedAt] = now.utc() }
        .singleOrNull()
        ?.toRecord()

    override suspend fun findByHash(tokenHash: String): RefreshTokenRecord? = db.io {
        RefreshTokensTable.selectAll().where { RefreshTokensTable.tokenHash eq tokenHash }.singleOrNull()?.toRecord()
    }

    override suspend fun revokeFamily(familyId: UUID, now: Instant): Int = db.io {
        RefreshTokensTable.update(
            where = { (RefreshTokensTable.familyId eq familyId) and RefreshTokensTable.revokedAt.isNull() },
        ) { it[revokedAt] = now.utc() }
    }

    private fun Instant.utc(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)

    private fun ResultRow.toRecord() = RefreshTokenRecord(
        id = this[RefreshTokensTable.id],
        userId = this[RefreshTokensTable.userId],
        familyId = this[RefreshTokensTable.familyId],
        tokenHash = this[RefreshTokensTable.tokenHash],
        createdAt = this[RefreshTokensTable.createdAt].toInstant(),
        expiresAt = this[RefreshTokensTable.expiresAt].toInstant(),
        usedAt = this[RefreshTokensTable.usedAt]?.toInstant(),
        revokedAt = this[RefreshTokensTable.revokedAt]?.toInstant(),
    )
}

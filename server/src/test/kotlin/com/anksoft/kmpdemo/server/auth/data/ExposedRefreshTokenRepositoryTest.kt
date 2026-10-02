package com.anksoft.kmpdemo.server.auth.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.anksoft.kmpdemo.server.auth.domain.NewUser
import com.anksoft.kmpdemo.server.auth.domain.RefreshTokenRecord
import org.jetbrains.exposed.v1.jdbc.Database
import com.anksoft.kmpdemo.server.support.withTestDatabase
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.async
import kotlin.test.Test

class ExposedRefreshTokenRepositoryTest {

    private val now = Instant.parse("2026-01-01T00:00:00Z")

    private suspend fun seedUser(db: Database): UUID {
        val id = UUID.randomUUID()
        ExposedUserRepository(db).create(NewUser(id, "$id@b.com", "hash"))
        return id
    }

    private fun record(userId: UUID, hash: String, family: UUID = UUID.randomUUID(), ttl: Duration = Duration.ofDays(30)) =
        RefreshTokenRecord(UUID.randomUUID(), userId, family, hash, now, now.plus(ttl), null, null)

    @Test
    fun `created record round trips through findByHash`() = withTestDatabase { db -> // AC-6
        val repo = ExposedRefreshTokenRepository(db)
        val userId = seedUser(db)
        val record = record(userId, "a".repeat(64))

        repo.create(record)

        assertThat(repo.findByHash("a".repeat(64))).isEqualTo(record)
        assertThat(repo.findByHash("b".repeat(64))).isNull()
    }

    @Test
    fun `markUsedIfActive consumes once and returns null on the second call`() = withTestDatabase { db -> // AC-7
        val repo = ExposedRefreshTokenRepository(db)
        repo.create(record(seedUser(db), "a".repeat(64)))

        val first = repo.markUsedIfActive("a".repeat(64), now.plusSeconds(1))
        val second = repo.markUsedIfActive("a".repeat(64), now.plusSeconds(2))

        assertThat(first).isNotNull()
        assertThat(second).isNull()
        assertThat(repo.findByHash("a".repeat(64))?.usedAt?.truncatedTo(ChronoUnit.SECONDS))
            .isEqualTo(now.plusSeconds(1))
    }

    @Test
    fun `markUsedIfActive returns null for an expired token`() = withTestDatabase { db -> // AC-7
        val repo = ExposedRefreshTokenRepository(db)
        repo.create(record(seedUser(db), "a".repeat(64), ttl = Duration.ofDays(1)))

        assertThat(repo.markUsedIfActive("a".repeat(64), now.plus(Duration.ofDays(2)))).isNull()
    }

    @Test
    fun `markUsedIfActive returns null for an unknown token`() = withTestDatabase { db ->
        assertThat(ExposedRefreshTokenRepository(db).markUsedIfActive("z".repeat(64), now)).isNull()
    }

    @Test
    fun `revokeFamily revokes only the family once and returns the count`() = withTestDatabase { db -> // AC-7
        val repo = ExposedRefreshTokenRepository(db)
        val userId = seedUser(db)
        val family = UUID.randomUUID()
        repo.create(record(userId, "a".repeat(64), family))
        repo.create(record(userId, "b".repeat(64), family))
        repo.create(record(userId, "c".repeat(64)))

        assertThat(repo.revokeFamily(family, now)).isEqualTo(2)
        assertThat(repo.revokeFamily(family, now)).isEqualTo(0)
        assertThat(repo.markUsedIfActive("a".repeat(64), now)).isNull()
        assertThat(repo.markUsedIfActive("c".repeat(64), now)).isNotNull()
    }

    @Test
    fun `concurrent consumption lets exactly one caller win`() = withTestDatabase { db -> // AC-6
        val repo = ExposedRefreshTokenRepository(db)
        repo.create(record(seedUser(db), "a".repeat(64)))

        val results = (1..8).map {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).let { scope ->
                scope.async { repo.markUsedIfActive("a".repeat(64), now.plusSeconds(1)) }
            }
        }.map { it.await() }

        assertThat(results.count { it != null }).isEqualTo(1)
    }

    @Test
    fun `rotate consumes the old token and stores the successor in its family`() = withTestDatabase { db -> // AC-6
        val repo = ExposedRefreshTokenRepository(db)
        val userId = seedUser(db)
        val family = UUID.randomUUID()
        repo.create(record(userId, "a".repeat(64), family))

        val consumed = repo.rotate("a".repeat(64), now.plusSeconds(1), record(UUID.randomUUID(), "b".repeat(64)))

        assertThat(consumed?.usedAt).isEqualTo(now.plusSeconds(1))
        val successor = repo.findByHash("b".repeat(64))
        assertThat(successor?.familyId).isEqualTo(family)
        assertThat(successor?.userId).isEqualTo(userId)
        assertThat(successor?.usedAt).isNull()
    }

    @Test
    fun `rotate inserts nothing when the old token is not active`() = withTestDatabase { db -> // AC-7
        val repo = ExposedRefreshTokenRepository(db)
        repo.create(record(seedUser(db), "a".repeat(64)))
        repo.markUsedIfActive("a".repeat(64), now.plusSeconds(1))

        val result = repo.rotate("a".repeat(64), now.plusSeconds(2), record(UUID.randomUUID(), "b".repeat(64)))

        assertThat(result).isNull()
        assertThat(repo.findByHash("b".repeat(64))).isNull()
    }
}

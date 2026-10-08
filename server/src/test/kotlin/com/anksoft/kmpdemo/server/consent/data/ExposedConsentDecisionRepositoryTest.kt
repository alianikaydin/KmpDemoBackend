package com.anksoft.kmpdemo.server.consent.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.anksoft.kmpdemo.server.auth.data.ExposedUserRepository
import com.anksoft.kmpdemo.server.auth.domain.NewUser
import com.anksoft.kmpdemo.server.consent.domain.ConsentDecisionStatus
import com.anksoft.kmpdemo.server.consent.domain.ConsentSource
import com.anksoft.kmpdemo.server.consent.domain.NewConsentDecision
import com.anksoft.kmpdemo.server.consent.domain.PURPOSE_OPTIONAL_DATA
import com.anksoft.kmpdemo.server.support.PostgresTestDb
import com.anksoft.kmpdemo.server.support.withTestDatabase
import java.time.Instant
import java.util.UUID
import kotlin.test.Test

class ExposedConsentDecisionRepositoryTest {

    private val at = Instant.parse("2026-10-08T12:00:00.123Z")

    private fun decision(
        status: ConsentDecisionStatus = ConsentDecisionStatus.GRANTED,
        version: Int = 1,
        language: String = "tr",
    ) = NewConsentDecision(PURPOSE_OPTIONAL_DATA, status, version, language, at, ConsentSource.UPDATE)

    private suspend fun createUser(db: org.jetbrains.exposed.v1.jdbc.Database, email: String = "a@b.com"): UUID {
        val id = UUID.randomUUID()
        ExposedUserRepository(db).create(NewUser(id, email, "\$argon2id\$hash"))
        return id
    }

    @Test
    fun `latest is null before any decision`() = withTestDatabase { db -> // AC-6
        val userId = createUser(db)

        assertThat(ExposedConsentDecisionRepository(db).latest(userId, PURPOSE_OPTIONAL_DATA)).isNull()
    }

    @Test
    fun `latest returns the row with the highest id and keeps every row`() = withTestDatabase { db -> // AC-15, AC-22
        val repo = ExposedConsentDecisionRepository(db)
        val userId = createUser(db)

        repo.append(userId, decision(ConsentDecisionStatus.GRANTED))
        repo.append(userId, decision(ConsentDecisionStatus.DENIED, language = "en"))

        val latest = repo.latest(userId, PURPOSE_OPTIONAL_DATA)!!
        assertThat(latest.status).isEqualTo(ConsentDecisionStatus.DENIED)
        assertThat(latest.textLanguage).isEqualTo("en")
        assertThat(latest.textVersion).isEqualTo(1)
        assertThat(latest.decidedAt).isEqualTo(at)
        assertThat(latest.source).isEqualTo(ConsentSource.UPDATE)
        assertThat(PostgresTestDb.countRows("consent_decisions")).isEqualTo(2)
    }

    @Test
    fun `latest only sees the decisions of the given user`() = withTestDatabase { db -> // AC-17
        val repo = ExposedConsentDecisionRepository(db)
        val first = createUser(db, "a@b.com")
        val second = createUser(db, "c@d.com")

        repo.append(first, decision())

        assertThat(repo.latest(second, PURPOSE_OPTIONAL_DATA)).isNull()
    }

    @Test
    fun `append returns false for an unknown user`() = withTestDatabase { db -> // AC-28
        val repo = ExposedConsentDecisionRepository(db)

        assertThat(repo.append(UUID.randomUUID(), decision())).isFalse()
        assertThat(PostgresTestDb.countRows("consent_decisions")).isEqualTo(0)
    }

    @Test
    fun `append returns true for an existing user`() = withTestDatabase { db -> // AC-8
        val repo = ExposedConsentDecisionRepository(db)

        assertThat(repo.append(createUser(db), decision())).isTrue()
    }

    @Test
    fun `append throws for a text version that does not exist`() = withTestDatabase { db -> // AC-5
        val repo = ExposedConsentDecisionRepository(db)
        val userId = createUser(db)

        assertThat(runCatching { repo.append(userId, decision(version = 999)) }.isFailure).isTrue()
        assertThat(PostgresTestDb.countRows("consent_decisions")).isEqualTo(0)
    }

    @Test
    fun `deleting a user removes its decisions`() = withTestDatabase { db -> // A2 (cascade)
        val repo = ExposedConsentDecisionRepository(db)
        val userId = createUser(db)
        repo.append(userId, decision())

        PostgresTestDb.execute("DELETE FROM users WHERE id = '$userId'")

        assertThat(PostgresTestDb.countRows("consent_decisions")).isEqualTo(0)
    }
}

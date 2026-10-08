package com.anksoft.kmpdemo.server.auth.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
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

class ExposedUserRepositoryTest {

    private fun newUser(email: String = "a@b.com") = NewUser(UUID.randomUUID(), email, "\$argon2id\$hash")

    @Test
    fun `create stores a user that can be found by email and id`() = withTestDatabase { db -> // AC-10
        val repo = ExposedUserRepository(db)
        val user = newUser()

        val created = repo.create(user)

        assertThat(created).isNotNull()
        assertThat(repo.findByEmail("a@b.com")?.user?.id).isEqualTo(user.id)
        assertThat(repo.findByEmail("a@b.com")?.passwordHash).isEqualTo("\$argon2id\$hash")
        assertThat(repo.findById(user.id)?.email).isEqualTo("a@b.com")
    }

    @Test
    fun `create returns null on unique violation`() = withTestDatabase { db -> // AC-2
        val repo = ExposedUserRepository(db)
        repo.create(newUser())

        assertThat(repo.create(newUser())).isNull()
    }

    @Test
    fun `lookups for unknown users return null`() = withTestDatabase { db ->
        val repo = ExposedUserRepository(db)

        assertThat(repo.findByEmail("nobody@b.com")).isNull()
        assertThat(repo.findById(UUID.randomUUID())).isNull()
    }

    private fun consent(version: Int = 1, language: String = "tr") = NewConsentDecision(
        PURPOSE_OPTIONAL_DATA, ConsentDecisionStatus.GRANTED, version, language,
        Instant.parse("2026-10-08T12:00:00Z"), ConsentSource.REGISTER,
    )

    @Test
    fun `create with consent stores the user and the decision together`() = withTestDatabase { db -> // AC-3
        val repo = ExposedUserRepository(db)
        val user = newUser()

        assertThat(repo.create(user, consent())).isNotNull()

        assertThat(PostgresTestDb.countRows("users")).isEqualTo(1)
        assertThat(PostgresTestDb.countRows("consent_decisions")).isEqualTo(1)
        val source = PostgresTestDb.query("SELECT source FROM consent_decisions") { it.getString(1) }
        assertThat(source).isEqualTo("register")
    }

    @Test
    fun `create rolls the user back when the decision insert fails`() = withTestDatabase { db -> // AC-5
        val repo = ExposedUserRepository(db)

        val failure = runCatching { repo.create(newUser(), consent(version = 999)) }

        assertThat(failure.isFailure).isTrue()
        assertThat(PostgresTestDb.countRows("users")).isEqualTo(0)
        assertThat(PostgresTestDb.countRows("consent_decisions")).isEqualTo(0)
    }

    @Test
    fun `create with consent returns null and writes no decision on a unique violation`() = withTestDatabase { db -> // AC-5
        val repo = ExposedUserRepository(db)
        repo.create(newUser())

        assertThat(repo.create(newUser(), consent())).isNull()
        assertThat(PostgresTestDb.countRows("consent_decisions")).isEqualTo(0)
    }
}

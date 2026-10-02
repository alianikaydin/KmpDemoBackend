package com.anksoft.kmpdemo.server.auth.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.anksoft.kmpdemo.server.auth.domain.NewUser
import com.anksoft.kmpdemo.server.support.withTestDatabase
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
}

package com.anksoft.kmpdemo.server.system

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.startsWith
import com.anksoft.kmpdemo.contract.auth.AuthResponseDto
import com.anksoft.kmpdemo.server.auth.domain.NewUser
import com.anksoft.kmpdemo.server.auth.domain.User
import com.anksoft.kmpdemo.server.auth.domain.UserCredentials
import com.anksoft.kmpdemo.server.auth.repository.UserRepository
import com.anksoft.kmpdemo.server.support.LogCapture
import com.anksoft.kmpdemo.server.support.PostgresTestDb
import com.anksoft.kmpdemo.server.support.login
import com.anksoft.kmpdemo.server.support.me
import com.anksoft.kmpdemo.server.support.postRaw
import com.anksoft.kmpdemo.server.support.refresh
import com.anksoft.kmpdemo.server.support.register
import com.anksoft.kmpdemo.server.support.withTestApp
import io.ktor.client.call.body
import org.koin.dsl.module
import java.util.UUID
import kotlin.test.Test

class SecretsNotLeakedTest {

    private val password = "S3cretPassw0rdX"

    @Test
    fun `password hash and refresh token hash in the database are not the plain values`() = withTestApp { client -> // AC-11
        val session = client.register("leak@example.com", password).body<AuthResponseDto>()

        val passwordHash = PostgresTestDb.query("SELECT password_hash FROM users") { it.getString(1) }
        val tokenHash = PostgresTestDb.query("SELECT token_hash FROM refresh_tokens") { it.getString(1) }

        assertThat(passwordHash).startsWith("\$argon2id\$")
        assertThat(passwordHash).doesNotContain(password)
        assertThat(tokenHash).isNotEqualTo(session.refreshToken)
        assertThat(tokenHash.length).isEqualTo(64)
    }

    @Test
    fun `logs never contain passwords or tokens across the whole auth flow`() { // AC-11
        val secrets = mutableListOf(password)
        val failing = object : UserRepository {
            override suspend fun findByEmail(email: String): UserCredentials? = throw IllegalStateException("db down")
            override suspend fun findById(id: UUID): User? = throw IllegalStateException("db down")
            override suspend fun create(user: NewUser): User? = throw IllegalStateException("db down")
        }

        LogCapture().use { logs ->
            withTestApp { client ->
                val registered = client.register("leak@example.com", password).body<AuthResponseDto>()
                val loggedIn = client.login("leak@example.com", password).body<AuthResponseDto>()
                val refreshed = client.refresh(registered.refreshToken!!).body<AuthResponseDto>()
                client.me(refreshed.accessToken)
                client.refresh(registered.refreshToken!!) // reuse path logs a warning
                client.postRaw("auth/register", """{"email":"x@y.com","password":"$password""")
                client.login("leak@example.com", "Wrong$password")
                listOf(registered, loggedIn, refreshed).forEach {
                    secrets += listOfNotNull(it.accessToken, it.refreshToken)
                }
            }
            withTestApp(extraModules = listOf(module { single<UserRepository> { failing } })) { client ->
                client.login("leak@example.com", password)
            }

            val text = logs.text()
            secrets.forEach { assertThat(text).doesNotContain(it) }
            assertThat(text).doesNotContain("Wrong$password")
        }
    }
}

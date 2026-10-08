package com.anksoft.kmpdemo.server.system

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import com.anksoft.kmpdemo.server.auth.domain.NewUser
import com.anksoft.kmpdemo.server.auth.domain.User
import com.anksoft.kmpdemo.server.auth.domain.UserCredentials
import com.anksoft.kmpdemo.server.auth.repository.UserRepository
import com.anksoft.kmpdemo.server.consent.domain.NewConsentDecision
import com.anksoft.kmpdemo.server.support.LogCapture
import com.anksoft.kmpdemo.server.support.login
import com.anksoft.kmpdemo.server.support.testSettings
import com.anksoft.kmpdemo.server.support.withTestApp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import org.koin.dsl.module
import org.testcontainers.postgresql.PostgreSQLContainer
import java.util.UUID
import kotlin.test.Test

class ErrorHandlingTest {

    private val failingRepository = object : UserRepository {
        override suspend fun findByEmail(email: String): UserCredentials? =
            throw IllegalStateException("SELECT * FROM users WHERE password = 'LeakyDetail1'")

        override suspend fun findById(id: UUID): User? = throw IllegalStateException("boom")
        override suspend fun create(user: NewUser, initialConsent: NewConsentDecision?): User? = throw IllegalStateException("boom")
    }

    @Test
    fun `unexpected exception returns a detail free 500`() { // AC-13
        LogCapture().use { logs ->
            withTestApp(extraModules = listOf(module { single<UserRepository> { failingRepository } })) { client ->
                val response = client.login()

                assertThat(response.status).isEqualTo(HttpStatusCode.InternalServerError)
                assertThat(response.bodyAsText())
                    .isEqualTo("""{"error":"internal_error","message":"Internal server error."}""")
            }
            assertThat(logs.text()).doesNotContain("LeakyDetail1")
        }
    }

    @Test
    fun `unreachable database returns 500 quickly and readiness reports 503`() { // AC-13
        val container = PostgreSQLContainer("postgres:17-alpine")
        container.start()
        try {
            val settings = testSettings {
                it.copy(
                    databaseUrl = container.jdbcUrl,
                    databaseUser = container.username,
                    databasePassword = container.password,
                )
            }
            withTestApp(settings, resetDb = false) { client ->
                assertThat(client.get("/health/ready").status).isEqualTo(HttpStatusCode.OK)
                container.stop()

                val started = System.nanoTime()
                val response = client.login()
                val elapsedMs = (System.nanoTime() - started) / 1_000_000

                assertThat(response.status).isEqualTo(HttpStatusCode.InternalServerError)
                assertThat(response.bodyAsText())
                    .isEqualTo("""{"error":"internal_error","message":"Internal server error."}""")
                assertThat(elapsedMs).isLessThan(5_000L)
                assertThat(client.get("/health/ready").status).isEqualTo(HttpStatusCode.ServiceUnavailable)
            }
        } finally {
            container.stop()
        }
    }
}

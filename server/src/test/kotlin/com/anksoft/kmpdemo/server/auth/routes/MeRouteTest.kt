package com.anksoft.kmpdemo.server.auth.routes

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import com.anksoft.kmpdemo.contract.auth.UserDto
import com.anksoft.kmpdemo.contract.error.ErrorCodes
import com.anksoft.kmpdemo.contract.error.ErrorResponseDto
import com.anksoft.kmpdemo.server.auth.security.JwtAccessTokenIssuer
import com.anksoft.kmpdemo.server.auth.security.JwtConfig
import com.anksoft.kmpdemo.server.support.MutableClock
import com.anksoft.kmpdemo.server.support.PostgresTestDb
import com.anksoft.kmpdemo.server.support.TEST_JWT_SECRET
import com.anksoft.kmpdemo.server.support.me
import com.anksoft.kmpdemo.server.support.registerOk
import com.anksoft.kmpdemo.server.support.withTestApp
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test

class MeRouteTest {

    private suspend fun assertRejected(response: HttpResponse, label: String) {
        assertThat(response.status, name = label).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(response.headers[HttpHeaders.WWWAuthenticate], name = label).isEqualTo("Bearer")
        assertThat(response.body<ErrorResponseDto>().error, name = label).isEqualTo(ErrorCodes.INVALID_TOKEN)
    }

    @Test
    fun `me returns the current user`() = withTestApp { client -> // AC-8
        val registered = client.registerOk("ali@example.com")

        val response = client.me(registered.accessToken)

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.body<UserDto>()).isEqualTo(registered.user)
        assertThat(response.bodyAsText()).doesNotContain("name")
    }

    @Test
    fun `missing and malformed tokens return 401`() = withTestApp { client -> // AC-9
        assertRejected(client.me(null), "no header")
        assertRejected(client.me(null, rawAuthorization = "Bearer garbage"), "garbage")
        assertRejected(client.me(null, rawAuthorization = "Basic abc"), "wrong scheme")
    }

    @Test
    fun `tokens with another secret or audience return 401`() = withTestApp { client -> // AC-9
        val forgedBySecret = JwtAccessTokenIssuer(
            JwtConfig("another-secret-another-secret-123456", "kmp-demo-server", "kmp-demo-app", Duration.ofMinutes(15)),
        ).issue(UUID.randomUUID(), Instant.now())
        val wrongAudience = JwtAccessTokenIssuer(
            JwtConfig(TEST_JWT_SECRET, "kmp-demo-server", "someone-else", Duration.ofMinutes(15)),
        ).issue(UUID.randomUUID(), Instant.now())

        assertRejected(client.me(forgedBySecret), "wrong secret")
        assertRejected(client.me(wrongAudience), "wrong audience")
    }

    @Test
    fun `expired access token returns 401`() { // AC-9
        val clock = MutableClock(Instant.now().minus(Duration.ofMinutes(16)))
        withTestApp(clock = clock) { client ->
            val registered = client.registerOk()

            assertRejected(client.me(registered.accessToken), "expired")
        }
    }

    @Test
    fun `valid token of a deleted user returns 401`() = withTestApp { client -> // AC-9
        val registered = client.registerOk()
        PostgresTestDb.execute("DELETE FROM users")

        assertRejected(client.me(registered.accessToken), "deleted user")
    }
}

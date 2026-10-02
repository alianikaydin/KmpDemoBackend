package com.anksoft.kmpdemo.server.auth.routes

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import com.anksoft.kmpdemo.contract.auth.AuthResponseDto
import com.anksoft.kmpdemo.contract.error.ErrorCodes
import com.anksoft.kmpdemo.contract.error.ErrorResponseDto
import com.anksoft.kmpdemo.server.support.MutableClock
import com.anksoft.kmpdemo.server.support.logout
import com.anksoft.kmpdemo.server.support.me
import com.anksoft.kmpdemo.server.support.refresh
import com.anksoft.kmpdemo.server.support.registerOk
import com.anksoft.kmpdemo.server.support.testSettings
import com.anksoft.kmpdemo.server.support.withTestApp
import io.ktor.client.call.body
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.time.Duration
import kotlin.test.Test

class RefreshRouteTest {

    @Test
    fun `refresh rotates tokens`() = withTestApp { client -> // AC-6
        val registered = client.registerOk()

        val first = client.refresh(registered.refreshToken!!)
        assertThat(first.status).isEqualTo(HttpStatusCode.OK)
        val rotated = first.body<AuthResponseDto>()
        assertThat(rotated.refreshToken).isNotEqualTo(registered.refreshToken)
        assertThat(rotated.user).isEqualTo(registered.user)
        assertThat(client.me(rotated.accessToken).status).isEqualTo(HttpStatusCode.OK)

        assertThat(client.refresh(rotated.refreshToken!!).status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun `unknown refresh token returns 401 without a challenge header`() = withTestApp { client -> // AC-7
        val response = client.refresh("never-issued-token")

        assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(response.body<ErrorResponseDto>().error).isEqualTo(ErrorCodes.INVALID_TOKEN)
        assertThat(response.headers[HttpHeaders.WWWAuthenticate]).isNull()
    }

    @Test
    fun `expired refresh token returns 401`() { // AC-7
        val clock = MutableClock()
        withTestApp(clock = clock) { client ->
            val registered = client.registerOk()

            clock.advance(Duration.ofDays(31))

            assertThat(client.refresh(registered.refreshToken!!).status).isEqualTo(HttpStatusCode.Unauthorized)
        }
    }

    @Test
    fun `refresh token is rejected after logout`() = withTestApp { client -> // AC-7
        val registered = client.registerOk()
        client.logout(registered.refreshToken!!)

        assertThat(client.refresh(registered.refreshToken!!).status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun `reusing a consumed token returns 401 and revokes the family`() = withTestApp { client -> // AC-7
        val registered = client.registerOk()
        val rotated = client.refresh(registered.refreshToken!!).body<AuthResponseDto>()

        assertThat(client.refresh(registered.refreshToken!!).status).isEqualTo(HttpStatusCode.Unauthorized)

        assertThat(client.refresh(rotated.refreshToken!!).status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun `empty refresh token returns 400`() = withTestApp { client -> // AC-3
        assertThat(client.refresh("").status).isEqualTo(HttpStatusCode.BadRequest)
    }
}

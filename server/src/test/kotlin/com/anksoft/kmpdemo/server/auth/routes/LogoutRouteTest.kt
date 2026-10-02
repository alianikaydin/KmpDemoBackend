package com.anksoft.kmpdemo.server.auth.routes

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.anksoft.kmpdemo.server.support.logout
import com.anksoft.kmpdemo.server.support.registerOk
import com.anksoft.kmpdemo.server.support.withTestApp
import io.ktor.http.HttpStatusCode
import kotlin.test.Test

class LogoutRouteTest {

    @Test
    fun `logout returns 204 and is idempotent`() = withTestApp { client -> // AC-7
        val registered = client.registerOk()

        assertThat(client.logout(registered.refreshToken!!).status).isEqualTo(HttpStatusCode.NoContent)
        assertThat(client.logout(registered.refreshToken!!).status).isEqualTo(HttpStatusCode.NoContent)
        assertThat(client.logout("never-issued").status).isEqualTo(HttpStatusCode.NoContent)
    }

    @Test
    fun `logout with an empty token returns 400`() = withTestApp { client -> // AC-3
        assertThat(client.logout("").status).isEqualTo(HttpStatusCode.BadRequest)
    }
}

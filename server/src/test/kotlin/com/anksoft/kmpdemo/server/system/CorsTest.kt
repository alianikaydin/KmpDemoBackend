package com.anksoft.kmpdemo.server.system

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.anksoft.kmpdemo.server.support.testSettings
import com.anksoft.kmpdemo.server.support.withTestApp
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test

class CorsTest {

    private suspend fun io.ktor.client.HttpClient.preflight(origin: String) = options("/api/v1/auth/login") {
        header(HttpHeaders.Origin, origin)
        header(HttpHeaders.AccessControlRequestMethod, "POST")
        header(HttpHeaders.AccessControlRequestHeaders, "content-type")
    }

    @Test
    fun `preflight from an allowed origin is accepted`() = withTestApp { client -> // AC-14
        val response = client.preflight("http://localhost:8080")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.headers[HttpHeaders.AccessControlAllowOrigin]).isEqualTo("http://localhost:8080")
        assertThat(response.headers[HttpHeaders.AccessControlAllowCredentials]).isNull()
    }

    @Test
    fun `preflight from another origin gets no allow origin header`() = withTestApp { client -> // AC-14
        val response = client.preflight("http://evil.example")

        assertThat(response.headers[HttpHeaders.AccessControlAllowOrigin]).isNull()
    }

    @Test
    fun `cors is off when no origins are configured`() { // AC-14
        withTestApp(testSettings { it.copy(corsAllowedOrigins = emptyList()) }) { client ->
            val response = client.preflight("http://localhost:8080")

            assertThat(response.headers[HttpHeaders.AccessControlAllowOrigin]).isNull()
        }
    }
}

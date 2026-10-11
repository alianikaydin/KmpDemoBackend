package com.anksoft.kmpdemo.server.appversion.routes

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.anksoft.kmpdemo.contract.app.VersionPolicyDto
import com.anksoft.kmpdemo.contract.error.ErrorCodes
import com.anksoft.kmpdemo.contract.error.ErrorResponseDto
import com.anksoft.kmpdemo.server.appversion.domain.AppVersion
import com.anksoft.kmpdemo.server.appversion.domain.VersionPolicy
import com.anksoft.kmpdemo.server.auth.security.JwtAccessTokenIssuer
import com.anksoft.kmpdemo.server.auth.security.JwtConfig
import com.anksoft.kmpdemo.server.support.LogCapture
import com.anksoft.kmpdemo.server.support.TEST_JWT_SECRET
import com.anksoft.kmpdemo.server.support.getVersionPolicy
import com.anksoft.kmpdemo.server.support.testSettings
import com.anksoft.kmpdemo.server.support.withTestApp
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test

class VersionPolicyRouteTest {

    private val configured = testSettings {
        it.copy(
            androidVersionPolicy = VersionPolicy(AppVersion(1, 0, 0), AppVersion(1, 2, 0)),
            iosVersionPolicy = VersionPolicy(AppVersion(2, 1, 0), AppVersion(3, 0, 1)),
        )
    }

    @Test
    fun `policy is served without authentication`() = withTestApp(configured) { client -> // AC-26
        val response = client.getVersionPolicy("android")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.body<VersionPolicyDto>()).isEqualTo(VersionPolicyDto("1.0.0", "1.2.0"))
    }

    @Test
    fun `each platform gets its own configured values`() = withTestApp(configured) { client -> // AC-26, AC-18
        assertThat(client.getVersionPolicy("android").body<VersionPolicyDto>())
            .isEqualTo(VersionPolicyDto("1.0.0", "1.2.0"))
        assertThat(client.getVersionPolicy("ios").body<VersionPolicyDto>())
            .isEqualTo(VersionPolicyDto("2.1.0", "3.0.1"))
    }

    @Test
    fun `a malformed authorization header never causes a 401`() = withTestApp(configured) { client -> // AC-26
        listOf("Bearer not-a-token", "Basic Zm9vOmJhcg==", "garbage").forEach { value ->
            val response = client.getVersionPolicy("android", authorization = value)

            assertThat(response.status, name = value).isEqualTo(HttpStatusCode.OK)
            assertThat(response.headers[HttpHeaders.WWWAuthenticate], name = value).isNull()
        }
    }

    @Test
    fun `an expired but well formed token never causes a 401`() = withTestApp(configured) { client -> // AC-26
        val longAgo = Instant.parse("2020-01-01T00:00:00Z")
        val expired = JwtAccessTokenIssuer(JwtConfig(TEST_JWT_SECRET, "kmp-demo-server", "kmp-demo-app", Duration.ofMinutes(15)))
            .issue(UUID.randomUUID(), longAgo)

        val response = client.getVersionPolicy("ios", authorization = "Bearer $expired")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.body<VersionPolicyDto>()).isEqualTo(VersionPolicyDto("2.1.0", "3.0.1"))
    }

    @Test
    fun `missing, empty, unknown and differently cased platforms are rejected`() =
        withTestApp(configured) { client -> // AC-26
            listOf(null, "", "web", "Android", "IOS", " ios", "android,ios").forEach { platform ->
                val response = client.getVersionPolicy(platform)

                assertThat(response.status, name = "platform=$platform").isEqualTo(HttpStatusCode.BadRequest)
                assertThat(response.body<ErrorResponseDto>().error, name = "platform=$platform")
                    .isEqualTo(ErrorCodes.INVALID_REQUEST)
            }
        }

    @Test
    fun `a server without configuration reports no update for both platforms`() = withTestApp { client -> // AC-27
        listOf("android", "ios").forEach { platform ->
            assertThat(client.getVersionPolicy(platform).body<VersionPolicyDto>(), name = platform)
                .isEqualTo(VersionPolicyDto("0.0.0", "0.0.0"))
        }
    }

    @Test
    fun `success and error responses are never cached`() = withTestApp(configured) { client -> // ADR-16
        assertThat(client.getVersionPolicy("android").headers[HttpHeaders.CacheControl]).isEqualTo("no-store")
        assertThat(client.getVersionPolicy("ios").headers[HttpHeaders.CacheControl]).isEqualTo("no-store")
        assertThat(client.getVersionPolicy("web").headers[HttpHeaders.CacheControl]).isEqualTo("no-store")
        assertThat(client.getVersionPolicy(null).headers[HttpHeaders.CacheControl]).isEqualTo("no-store")
    }

    @Test
    fun `the request log line carries no query string`() = withTestApp(configured) { client -> // AC-21
        LogCapture().use { logs ->
            client.getVersionPolicy("android", authorization = "Bearer secret-token-value")

            val text = logs.text()
            assertThat(text).contains("GET /api/v1/app/version-policy -> 200")
            assertThat(text).doesNotContain("platform=")
            assertThat(text).doesNotContain("secret-token-value")
        }
    }

    @Test
    fun `other methods are not allowed`() = withTestApp(configured) { client -> // ADR-16
        val response = client.post("/api/v1/app/version-policy?platform=android")

        assertThat(response.status).isEqualTo(HttpStatusCode.MethodNotAllowed)
    }
}

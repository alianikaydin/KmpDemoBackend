package com.anksoft.kmpdemo.server.auth.routes

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNotEmpty
import com.anksoft.kmpdemo.contract.auth.AuthPaths
import com.anksoft.kmpdemo.contract.auth.AuthResponseDto
import com.anksoft.kmpdemo.contract.consent.ConsentDecisionDto
import com.anksoft.kmpdemo.contract.consent.ConsentStatus
import com.anksoft.kmpdemo.contract.error.ErrorCodes
import com.anksoft.kmpdemo.contract.error.ErrorResponseDto
import com.anksoft.kmpdemo.server.support.PostgresTestDb
import com.anksoft.kmpdemo.server.support.login
import com.anksoft.kmpdemo.server.support.postRaw
import com.anksoft.kmpdemo.server.support.refresh
import com.anksoft.kmpdemo.server.support.register
import com.anksoft.kmpdemo.server.support.withTestApp
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.UUID
import kotlin.test.Test

class RegisterRouteTest {

    @Test
    fun `register returns tokens and user`() = withTestApp { client -> // AC-1
        val response = client.register(" Ali@Example.COM ", "Password1")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val body = response.body<AuthResponseDto>()
        assertThat(body.accessToken).isNotEmpty()
        assertThat(body.refreshToken).isNotNull()
        assertThat(body.refreshToken!!.length).isEqualTo(43)
        assertThat(body.user.email).isEqualTo("ali@example.com")
        UUID.fromString(body.user.id)
        assertThat(PostgresTestDb.countRows("users")).isEqualTo(1)
    }

    @Test
    fun `duplicate email with different case and whitespace returns 409`() = withTestApp { client -> // AC-2
        client.register("ali@example.com")

        val response = client.register(" Ali@Example.COM ")

        assertThat(response.status).isEqualTo(HttpStatusCode.Conflict)
        assertThat(response.body<ErrorResponseDto>().error).isEqualTo(ErrorCodes.EMAIL_TAKEN)
        assertThat(PostgresTestDb.countRows("users")).isEqualTo(1)
    }

    @Test
    fun `concurrent duplicate registers create one user`() = withTestApp { client -> // AC-2
        val statuses = coroutineScope {
            (1..10).map { async { client.register("race@example.com").status } }.awaitAll()
        }

        assertThat(statuses.count { it == HttpStatusCode.OK }).isEqualTo(1)
        assertThat(statuses.count { it == HttpStatusCode.Conflict }).isEqualTo(9)
        assertThat(PostgresTestDb.countRows("users")).isEqualTo(1)
    }

    @Test
    fun `invalid credentials return 400 and create nothing`() = withTestApp { client -> // AC-3
        listOf(
            "" to "Password1",
            "not-an-email" to "Password1",
            "a@b.com" to "Short1A",
            "a@b.com" to "NoDigitsHere",
            "a@b.com" to "nouppercase1",
            "a@b.com" to "A1" + "a".repeat(128),
        ).forEach { (email, password) ->
            val response = client.register(email, password)

            assertThat(response.status, name = "$email/${password.length}").isEqualTo(HttpStatusCode.BadRequest)
            assertThat(response.body<ErrorResponseDto>().error).isEqualTo(ErrorCodes.INVALID_REQUEST)
        }
        assertThat(PostgresTestDb.countRows("users")).isEqualTo(0)
    }

    @Test
    fun `email longer than 254 characters returns 400 and creates nothing`() = withTestApp { client -> // AC-3
        val response = client.register("a".repeat(250) + "@b.com")

        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
        assertThat(PostgresTestDb.countRows("users")).isEqualTo(0)
    }

    @Test
    fun `malformed json and missing fields return 400`() = withTestApp { client -> // AC-3
        listOf("{not json", "", "[]", """{"email":"a@b.com"}""", """{"password":"Password1"}""").forEach { raw ->
            val response = client.postRaw(AuthPaths.REGISTER, raw)

            assertThat(response.status, name = raw).isEqualTo(HttpStatusCode.BadRequest)
            assertThat(response.body<ErrorResponseDto>().error).isEqualTo(ErrorCodes.INVALID_REQUEST)
        }
        assertThat(PostgresTestDb.countRows("users")).isEqualTo(0)
    }

    @Test
    fun `non json content type returns 415`() = withTestApp { client -> // AC-3
        val response = client.post("/api/v1/auth/register") {
            contentType(ContentType.Text.Plain)
            setBody("""{"email":"a@b.com","password":"Password1"}""")
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.UnsupportedMediaType)
        assertThat(PostgresTestDb.countRows("users")).isEqualTo(0)
    }

    @Test
    fun `register with granted consent stores the decision and returns it`() = withTestApp { client -> // AC-3
        val response = client.register(consent = ConsentDecisionDto("granted", 1, "tr"))

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val consent = response.body<AuthResponseDto>().consent
        assertThat(consent?.status).isEqualTo(ConsentStatus.GRANTED)
        assertThat(consent?.textVersion).isEqualTo(1)
        assertThat(consent?.textLanguage).isEqualTo("tr")
        assertThat(consent?.decidedAt).isNotNull()
        assertThat(PostgresTestDb.countRows("consent_decisions")).isEqualTo(1)
        assertThat(PostgresTestDb.query("SELECT source FROM consent_decisions") { it.getString(1) }).isEqualTo("register")
    }

    @Test
    fun `register with denied consent stores the decision`() = withTestApp { client -> // AC-2
        val response = client.register(consent = ConsentDecisionDto("denied", 1, "en"))

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.body<AuthResponseDto>().consent?.status).isEqualTo(ConsentStatus.DENIED)
        assertThat(PostgresTestDb.countRows("consent_decisions")).isEqualTo(1)
    }

    @Test
    fun `an old register body without consent still works and answers none`() = withTestApp { client -> // AC-4
        val response = client.postRaw(AuthPaths.REGISTER, """{"email":"old@example.com","password":"Password1"}""")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.body<AuthResponseDto>().consent?.status).isEqualTo(ConsentStatus.NONE)
        assertThat(PostgresTestDb.countRows("users")).isEqualTo(1)
        assertThat(PostgresTestDb.countRows("consent_decisions")).isEqualTo(0)
    }

    @Test
    fun `register with an unknown consent version returns 422 and creates no account`() = withTestApp { client -> // AC-5
        val response = client.register(consent = ConsentDecisionDto("granted", 999, "tr"))

        assertThat(response.status).isEqualTo(HttpStatusCode.UnprocessableEntity)
        assertThat(response.body<ErrorResponseDto>().error).isEqualTo(ErrorCodes.UNKNOWN_CONSENT_VERSION)
        assertThat(PostgresTestDb.countRows("users")).isEqualTo(0)
    }

    @Test
    fun `register with an invalid consent status returns 400 and creates no account`() = withTestApp { client -> // AC-5
        val response = client.register(consent = ConsentDecisionDto("maybe", 1, "tr"))

        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
        assertThat(response.body<ErrorResponseDto>().error).isEqualTo(ErrorCodes.INVALID_REQUEST)
        assertThat(PostgresTestDb.countRows("users")).isEqualTo(0)
    }

    @Test
    fun `login and refresh responses leave the consent out`() = withTestApp { client -> // AC-17
        val registered = client.register(consent = ConsentDecisionDto("granted", 1, "tr")).body<AuthResponseDto>()

        val login = client.login().bodyAsText()
        val refreshed = client.refresh(registered.refreshToken!!).bodyAsText()

        assertThat(login).doesNotContain("consent")
        assertThat(refreshed).doesNotContain("consent")
    }
}

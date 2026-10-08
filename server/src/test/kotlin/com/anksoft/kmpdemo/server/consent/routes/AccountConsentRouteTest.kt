package com.anksoft.kmpdemo.server.consent.routes

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.anksoft.kmpdemo.contract.consent.AccountConsentDto
import com.anksoft.kmpdemo.contract.consent.ConsentDecisionDto
import com.anksoft.kmpdemo.contract.consent.ConsentStatus
import com.anksoft.kmpdemo.contract.error.ErrorCodes
import com.anksoft.kmpdemo.contract.error.ErrorResponseDto
import com.anksoft.kmpdemo.server.support.MutableClock
import com.anksoft.kmpdemo.server.support.PostgresTestDb
import com.anksoft.kmpdemo.server.support.getAccountConsent
import com.anksoft.kmpdemo.server.support.login
import com.anksoft.kmpdemo.server.support.putAccountConsent
import com.anksoft.kmpdemo.server.support.putAccountConsentRaw
import com.anksoft.kmpdemo.server.support.registerOk
import com.anksoft.kmpdemo.server.support.withTestApp
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import com.anksoft.kmpdemo.contract.auth.AuthResponseDto

class AccountConsentRouteTest {

    private fun decision(status: String = "granted", version: Int = 1, language: String = "tr") =
        ConsentDecisionDto(status, version, language)

    private suspend fun assertUnauthorized(response: HttpResponse, label: String) {
        assertThat(response.status, name = label).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(response.headers[HttpHeaders.WWWAuthenticate], name = label).isEqualTo("Bearer")
        assertThat(response.body<ErrorResponseDto>().error, name = label).isEqualTo(ErrorCodes.INVALID_TOKEN)
    }

    @Test
    fun `get and put without a token return 401 with a challenge`() = withTestApp { client -> // AC-28
        assertUnauthorized(client.getAccountConsent(null), "get")
        assertUnauthorized(client.putAccountConsent(null, decision()), "put")
    }

    @Test
    fun `a new account has no decision`() = withTestApp { client -> // AC-6
        val session = client.registerOk()

        val response = client.getAccountConsent(session.accessToken)

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.bodyAsText()).isEqualTo("""{"status":"none","reconsent_required":false}""")
    }

    @Test
    fun `put granted is stored and read back with the server time`() = MutableClock().let { clock ->
        withTestApp(clock = clock) { client -> // AC-8, AC-22
            val session = client.registerOk()

            val put = client.putAccountConsent(session.accessToken, decision("granted", 1, "tr"))

            assertThat(put.status).isEqualTo(HttpStatusCode.OK)
            val expected = clock.instant().truncatedTo(ChronoUnit.MILLIS).toString()
            val read = client.getAccountConsent(session.accessToken).body<AccountConsentDto>()
            assertThat(read).isEqualTo(
                AccountConsentDto(ConsentStatus.GRANTED, 1, "tr", expected, reconsentRequired = false),
            )
            assertThat(put.body<AccountConsentDto>()).isEqualTo(read)
            val stored = PostgresTestDb.query("SELECT decided_at, source FROM consent_decisions") {
                it.getObject(1, OffsetDateTime::class.java).toInstant() to it.getString(2)
            }
            assertThat(stored.first).isEqualTo(Instant.parse(expected))
            assertThat(stored.second).isEqualTo("update")
        }
    }

    @Test
    fun `a later denied decision replaces granted and the history keeps both rows`() = withTestApp { client -> // AC-9, AC-15
        val session = client.registerOk()
        client.putAccountConsent(session.accessToken, decision("granted"))

        val denied = client.putAccountConsent(session.accessToken, decision("denied", language = "en"))

        assertThat(denied.body<AccountConsentDto>().status).isEqualTo(ConsentStatus.DENIED)
        assertThat(client.getAccountConsent(session.accessToken).body<AccountConsentDto>().textLanguage).isEqualTo("en")
        assertThat(PostgresTestDb.countRows("consent_decisions")).isEqualTo(2)
    }

    @Test
    fun `putting the same decision twice stores one row`() = withTestApp { client -> // AC-8
        val session = client.registerOk()

        val first = client.putAccountConsent(session.accessToken, decision())
        val second = client.putAccountConsent(session.accessToken, decision())

        assertThat(second.status).isEqualTo(HttpStatusCode.OK)
        assertThat(second.body<AccountConsentDto>()).isEqualTo(first.body<AccountConsentDto>())
        assertThat(PostgresTestDb.countRows("consent_decisions")).isEqualTo(1)
    }

    @Test
    fun `another device sees the decision after logging in`() = withTestApp { client -> // AC-6, AC-17
        val session = client.registerOk("ali@example.com")
        client.putAccountConsent(session.accessToken, decision("granted"))

        val otherDevice = client.login("ali@example.com").body<AuthResponseDto>()
        val read = client.getAccountConsent(otherDevice.accessToken).body<AccountConsentDto>()

        assertThat(read.status).isEqualTo(ConsentStatus.GRANTED)
    }

    @Test
    fun `decisions of one account are invisible to another`() = withTestApp { client -> // AC-17
        val ali = client.registerOk("ali@example.com")
        val veli = client.registerOk("veli@example.com")
        client.putAccountConsent(ali.accessToken, decision("granted"))

        val read = client.getAccountConsent(veli.accessToken).body<AccountConsentDto>()

        assertThat(read.status).isEqualTo(ConsentStatus.NONE)
        assertThat(read.textVersion).isNull()
    }

    @Test
    fun `invalid status or body returns 400`() = withTestApp { client -> // AC-9
        val session = client.registerOk()
        val bad = listOf(
            """{"status":"none","text_version":1,"text_language":"tr"}""",
            """{"status":"maybe","text_version":1,"text_language":"tr"}""",
            """{"status":"granted","text_language":"tr"}""",
            """{"status":"granted","text_version":1}""",
            """{"text_version":1,"text_language":"tr"}""",
            "{not json",
            "[]",
        )
        bad.forEach { raw ->
            val response = client.putAccountConsentRaw(session.accessToken, raw)

            assertThat(response.status, name = raw).isEqualTo(HttpStatusCode.BadRequest)
            assertThat(response.body<ErrorResponseDto>().error, name = raw).isEqualTo(ErrorCodes.INVALID_REQUEST)
        }
        assertThat(PostgresTestDb.countRows("consent_decisions")).isEqualTo(0)
    }

    @Test
    fun `unknown text version or language returns 422`() = withTestApp { client -> // AC-5
        val session = client.registerOk()

        listOf(decision(version = 999), decision(language = "de")).forEach { dto ->
            val response = client.putAccountConsent(session.accessToken, dto)

            assertThat(response.status, name = "$dto").isEqualTo(HttpStatusCode.UnprocessableEntity)
            assertThat(response.body<ErrorResponseDto>()).isEqualTo(
                ErrorResponseDto("unknown_consent_version", "Unknown consent text version."),
            )
        }
        assertThat(PostgresTestDb.countRows("consent_decisions")).isEqualTo(0)
    }

    @Test
    fun `a token of a deleted user gets 401 on put`() = withTestApp { client -> // AC-28
        val session = client.registerOk()
        PostgresTestDb.execute("DELETE FROM users")

        assertUnauthorized(client.putAccountConsent(session.accessToken, decision()), "deleted user")
    }

    @Test
    fun `granted consent is flagged for reconsent after a flagged version but not changed`() = withTestApp { client -> // AC-23
        val session = client.registerOk()
        client.putAccountConsent(session.accessToken, decision("granted"))

        PostgresTestDb.insertTextVersion(9001, requiresReconsent = false)
        assertThat(client.getAccountConsent(session.accessToken).body<AccountConsentDto>().reconsentRequired).isFalse()

        PostgresTestDb.insertTextVersion(9002, requiresReconsent = true)
        val read = client.getAccountConsent(session.accessToken).body<AccountConsentDto>()
        assertThat(read.reconsentRequired).isTrue()
        assertThat(read.status).isEqualTo(ConsentStatus.GRANTED)
        assertThat(read.textVersion).isEqualTo(1)
    }

    @Test
    fun `denied consent is never flagged for reconsent`() = withTestApp { client -> // AC-23
        val session = client.registerOk()
        client.putAccountConsent(session.accessToken, decision("denied"))
        PostgresTestDb.insertTextVersion(9001, requiresReconsent = true)

        val read = client.getAccountConsent(session.accessToken).body<AccountConsentDto>()

        assertThat(read.reconsentRequired).isFalse()
        assertThat(read.decidedAt).isNotNull()
    }
}

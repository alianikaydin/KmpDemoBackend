package com.anksoft.kmpdemo.server.consent.routes

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.anksoft.kmpdemo.contract.consent.ConsentTextsDto
import com.anksoft.kmpdemo.server.support.getTexts
import com.anksoft.kmpdemo.server.support.withTestApp
import io.ktor.client.call.body
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test

class ConsentTextsRouteTest {

    @Test
    fun `texts are served without authentication in the requested language`() = withTestApp { client -> // AC-21
        val response = client.getTexts("tr")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val body = response.body<ConsentTextsDto>()
        assertThat(body.version).isEqualTo(1)
        assertThat(body.language).isEqualTo("tr")
        assertThat(body.label).isEqualTo("İsteğe bağlı veri toplamaya izin ver")
        assertThat(body.policyUrl).isEqualTo("https://example.com/privacy")
    }

    @Test
    fun `texts carry a cache control header`() = withTestApp { client -> // AC-21
        val response = client.getTexts("en")

        assertThat(response.headers[HttpHeaders.CacheControl]).isEqualTo("max-age=300")
    }

    @Test
    fun `a bogus authorization header is ignored`() = withTestApp { client -> // AC-21
        val response = client.getTexts("en", authorization = "Bearer not-a-token")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun `missing, unsupported and malformed languages fall back to english`() = withTestApp { client -> // AC-24
        listOf(null, "", "de", "xx-!!", "!!").forEach { lang ->
            val body = client.getTexts(lang).body<ConsentTextsDto>()

            assertThat(body.language, name = "lang=$lang").isEqualTo("en")
            assertThat(body.version, name = "lang=$lang").isEqualTo(1)
        }
    }

    @Test
    fun `region and case variants map to the base language`() = withTestApp { client -> // AC-24
        listOf("tr-TR", "TR", " tr_tr ").forEach { lang ->
            assertThat(client.getTexts(lang).body<ConsentTextsDto>().language, name = "lang=$lang").isEqualTo("tr")
        }
    }

    @Test
    fun `texts are served from the latest version`() = withTestApp { client -> // AC-21
        com.anksoft.kmpdemo.server.support.PostgresTestDb.insertTextVersion(9001, requiresReconsent = false)

        val body = client.getTexts("tr").body<ConsentTextsDto>()

        assertThat(body.version).isEqualTo(9001)
        assertThat(body.label).contains("label 9001")
        assertThat(client.getTexts("tr").headers[HttpHeaders.CacheControl]).isNotNull()
    }
}

package com.anksoft.kmpdemo.server.system

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.anksoft.kmpdemo.server.support.withTestApp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test

class HealthRouteTest {

    @Test
    fun `live returns 200 UP without touching the database`() { // AC-16
        withTestApp { client ->
            val response = client.get("/health/live")

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            assertThat(response.bodyAsText()).isEqualTo("""{"status":"UP"}""")
        }
    }

    @Test
    fun `ready returns 200 UP when the database answers`() { // AC-16
        withTestApp { client ->
            val response = client.get("/health/ready")

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            assertThat(response.bodyAsText()).isEqualTo("""{"status":"UP"}""")
        }
    }

    @Test
    fun `response echoes a safe request id and replaces an unsafe one`() {
        withTestApp { client ->
            val echoed = client.get("/health/live") { header("X-Request-Id", "abc-123") }
            assertThat(echoed.headers["X-Request-Id"]).isEqualTo("abc-123")

            val replaced = client.get("/health/live") { header("X-Request-Id", "bad id\twith spaces") }
            assertThat(replaced.status).isEqualTo(HttpStatusCode.OK)
            assertThat(replaced.headers["X-Request-Id"]?.matches(Regex("^[0-9a-f-]{36}$"))).isEqualTo(true)
        }
    }
}

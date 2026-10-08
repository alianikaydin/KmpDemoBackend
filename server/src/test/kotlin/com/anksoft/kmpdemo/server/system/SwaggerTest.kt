package com.anksoft.kmpdemo.server.system

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import com.anksoft.kmpdemo.server.support.testSettings
import com.anksoft.kmpdemo.server.support.withTestApp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test

class SwaggerTest {

    @Test
    fun `swagger ui and the openapi document are served when enabled`() { // AC-16
        withTestApp(testSettings { it.copy(swaggerEnabled = true) }) { client ->
            val ui = client.get("/swagger")
            assertThat(ui.status).isEqualTo(HttpStatusCode.OK)
            assertThat(ui.bodyAsText()).contains("swagger")

            val spec = client.get("/swagger/documentation.yaml").bodyAsText()
            listOf("/auth/register", "/auth/login", "/auth/refresh", "/auth/me", "/auth/logout", "/consent/texts", "/account/consent").forEach {
                assertThat(spec).contains(it)
            }
        }
    }

    @Test
    fun `swagger is not served by default`() = withTestApp { client ->
        assertThat(client.get("/swagger").status).isEqualTo(HttpStatusCode.NotFound)
    }
}

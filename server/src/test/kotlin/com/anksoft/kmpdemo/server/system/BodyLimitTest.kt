package com.anksoft.kmpdemo.server.system

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.anksoft.kmpdemo.contract.error.ErrorCodes
import com.anksoft.kmpdemo.contract.error.ErrorResponseDto
import com.anksoft.kmpdemo.server.plugins.MAX_BODY_BYTES
import com.anksoft.kmpdemo.server.support.withTestApp
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlin.test.Test

class BodyLimitTest {

    private fun Application.echoRoute() {
        routing { post("/echo") { call.respondText(call.receiveText().length.toString()) } }
    }

    @Test
    fun `body larger than 16 KiB is rejected with 413`() { // AC-3
        withTestApp(extra = { echoRoute() }) { client ->
            val response = client.post("/echo") {
                contentType(ContentType.Application.Json)
                setBody("x".repeat((MAX_BODY_BYTES + 1024).toInt()))
            }

            assertThat(response.status).isEqualTo(HttpStatusCode.PayloadTooLarge)
            assertThat(response.body<ErrorResponseDto>().error).isEqualTo(ErrorCodes.PAYLOAD_TOO_LARGE)
        }
    }

    @Test
    fun `body within the limit is accepted`() {
        withTestApp(extra = { echoRoute() }) { client ->
            val response = client.post("/echo") {
                contentType(ContentType.Application.Json)
                setBody("x".repeat(1024))
            }

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        }
    }
}

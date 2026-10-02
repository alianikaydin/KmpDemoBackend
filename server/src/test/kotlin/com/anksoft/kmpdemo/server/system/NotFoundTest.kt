package com.anksoft.kmpdemo.server.system

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.anksoft.kmpdemo.contract.error.ErrorCodes
import com.anksoft.kmpdemo.contract.error.ErrorResponseDto
import com.anksoft.kmpdemo.server.support.withTestApp
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlin.test.Test

class NotFoundTest {

    @Test
    fun `unknown path returns 404 with the standard error body`() { // AC-13
        withTestApp { client ->
            val response = client.get("/api/v1/nope")

            assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
            assertThat(response.body<ErrorResponseDto>()).isEqualTo(ErrorResponseDto(ErrorCodes.NOT_FOUND, "Not found."))
        }
    }

    @Test
    fun `wrong method on a known path returns 405 with the standard error body`() { // AC-13
        withTestApp { client ->
            val response = client.get("/api/v1/auth/login")

            assertThat(response.status).isEqualTo(HttpStatusCode.MethodNotAllowed)
            assertThat(response.body<ErrorResponseDto>().error).isEqualTo(ErrorCodes.METHOD_NOT_ALLOWED)
        }
    }
}

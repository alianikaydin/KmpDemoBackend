package com.anksoft.kmpdemo.server.system

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.anksoft.kmpdemo.contract.error.ErrorCodes
import com.anksoft.kmpdemo.contract.error.ErrorResponseDto
import com.anksoft.kmpdemo.server.support.login
import com.anksoft.kmpdemo.server.support.testSettings
import com.anksoft.kmpdemo.server.support.withTestApp
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test

class RateLimitTest {

    @Test
    fun `fourth auth request within a minute gets 429 with retry after`() { // Plan decision 5 (rate limit)
        withTestApp(testSettings { it.copy(rateLimitAuthPerMinute = 3) }) { client ->
            repeat(3) { assertThat(client.login().status).isEqualTo(HttpStatusCode.Unauthorized) }

            val limited = client.login()

            assertThat(limited.status).isEqualTo(HttpStatusCode.TooManyRequests)
            assertThat(limited.headers[HttpHeaders.RetryAfter]).isNotNull()
            assertThat(limited.body<ErrorResponseDto>().error).isEqualTo(ErrorCodes.RATE_LIMITED)
        }
    }

    @Test
    fun `health endpoints are not rate limited`() { // AC-16, plan decision 5
        withTestApp(testSettings { it.copy(rateLimitAuthPerMinute = 1) }) { client ->
            repeat(5) { assertThat(client.get("/health/live").status).isEqualTo(HttpStatusCode.OK) }
        }
    }

    @Test
    fun `rate limit of zero disables limiting`() = withTestApp { client -> // Plan decision 5 (0 = disabled)
        repeat(30) { assertThat(client.login().status).isEqualTo(HttpStatusCode.Unauthorized) }
    }
}

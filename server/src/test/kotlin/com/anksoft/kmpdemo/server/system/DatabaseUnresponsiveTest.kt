package com.anksoft.kmpdemo.server.system

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import com.anksoft.kmpdemo.server.support.PostgresTestDb
import com.anksoft.kmpdemo.server.support.login
import com.anksoft.kmpdemo.server.support.withTestApp
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import java.time.Duration
import kotlin.system.measureTimeMillis
import kotlin.test.Test

class DatabaseUnresponsiveTest {

    @Test
    fun `login fails fast with 500 and ready returns 503 while the database is frozen`() { // AC-13
        withTestApp { client ->
            // The application starts lazily on the first request; start it before freezing the database.
            assertThat(client.get("/health/ready").status).isEqualTo(HttpStatusCode.OK)
            PostgresTestDb.pause()
            try {
                var loginStatus: HttpStatusCode? = null
                val loginMillis = measureTimeMillis { loginStatus = client.login().status }
                assertThat(loginStatus).isEqualTo(HttpStatusCode.InternalServerError)
                assertThat(loginMillis).isLessThan(Duration.ofSeconds(10).toMillis())

                var readyStatus: HttpStatusCode? = null
                val readyMillis = measureTimeMillis { readyStatus = client.get("/health/ready").status }
                assertThat(readyStatus).isEqualTo(HttpStatusCode.ServiceUnavailable)
                assertThat(readyMillis).isLessThan(Duration.ofSeconds(10).toMillis())
            } finally {
                PostgresTestDb.unpause()
            }
        }
    }
}

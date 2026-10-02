package com.anksoft.kmpdemo.server.system

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.anksoft.kmpdemo.server.support.login
import com.anksoft.kmpdemo.server.support.registerOk
import com.anksoft.kmpdemo.server.support.withTestApp
import io.ktor.http.HttpStatusCode
import kotlin.test.Test

class PersistenceRestartTest {

    @Test
    fun `user can log in after the server restarts`() { // AC-10
        withTestApp { client -> client.registerOk("restart@example.com") }

        // A second application with a new pool and a new Koin context on the same database.
        withTestApp(resetDb = false) { client ->
            assertThat(client.login("restart@example.com").status).isEqualTo(HttpStatusCode.OK)
        }
    }
}

package com.anksoft.kmpdemo.server.system

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.anksoft.kmpdemo.contract.auth.AuthResponseDto
import com.anksoft.kmpdemo.server.support.me
import com.anksoft.kmpdemo.server.support.refresh
import com.anksoft.kmpdemo.server.support.registerOk
import com.anksoft.kmpdemo.server.support.withTestApp
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlin.test.Test

class MultiInstanceTest {

    @Test
    fun `tokens issued by one instance are accepted by another`() { // AC-12
        withTestApp { clientA ->
            val registered = clientA.registerOk("multi@example.com")

            withTestApp(resetDb = false) { clientB ->
                assertThat(clientB.me(registered.accessToken).status).isEqualTo(HttpStatusCode.OK)

                val refreshed = clientB.refresh(registered.refreshToken!!)
                assertThat(refreshed.status).isEqualTo(HttpStatusCode.OK)

                val fromB = refreshed.body<AuthResponseDto>()
                assertThat(clientA.me(fromB.accessToken).status).isEqualTo(HttpStatusCode.OK)
            }
        }
    }
}

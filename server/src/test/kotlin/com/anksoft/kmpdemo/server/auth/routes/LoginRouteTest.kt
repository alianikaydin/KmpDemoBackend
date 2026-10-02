package com.anksoft.kmpdemo.server.auth.routes

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import com.anksoft.kmpdemo.contract.auth.AuthResponseDto
import com.anksoft.kmpdemo.contract.error.ErrorCodes
import com.anksoft.kmpdemo.contract.error.ErrorResponseDto
import com.anksoft.kmpdemo.server.support.login
import com.anksoft.kmpdemo.server.support.registerOk
import com.anksoft.kmpdemo.server.support.withTestApp
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test

class LoginRouteTest {

    @Test
    fun `login with valid credentials returns a new token pair`() = withTestApp { client -> // AC-4
        val registered = client.registerOk()

        val response = client.login(" ALI@example.com ")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val body = response.body<AuthResponseDto>()
        assertThat(body.user).isEqualTo(registered.user)
        assertThat(body.accessToken).isNotEqualTo(registered.accessToken)
        assertThat(body.refreshToken).isNotEqualTo(registered.refreshToken)
    }

    @Test
    fun `wrong password and unknown email return identical 401`() = withTestApp { client -> // AC-5
        client.registerOk()

        val wrongPassword = client.login(password = "Wrong1234")
        val unknownEmail = client.login(email = "nobody@example.com")

        assertThat(wrongPassword.status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(unknownEmail.status).isEqualTo(wrongPassword.status)
        assertThat(unknownEmail.bodyAsText()).isEqualTo(wrongPassword.bodyAsText())
        assertThat(wrongPassword.body<ErrorResponseDto>().error).isEqualTo(ErrorCodes.INVALID_CREDENTIALS)
        assertThat(wrongPassword.headers[HttpHeaders.WWWAuthenticate]).isNull()
        assertThat(unknownEmail.headers[HttpHeaders.WWWAuthenticate]).isNull()
        assertThat(unknownEmail.headers[HttpHeaders.ContentType]).isEqualTo(wrongPassword.headers[HttpHeaders.ContentType])
    }

    @Test
    fun `empty credentials return 400`() = withTestApp { client -> // AC-3
        assertThat(client.login(email = "", password = "x").status).isEqualTo(HttpStatusCode.BadRequest)
        assertThat(client.login(email = "a@b.com", password = "").status).isEqualTo(HttpStatusCode.BadRequest)
    }
}

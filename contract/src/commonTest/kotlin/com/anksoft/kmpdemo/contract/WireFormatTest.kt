package com.anksoft.kmpdemo.contract

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.anksoft.kmpdemo.contract.auth.AuthResponseDto
import com.anksoft.kmpdemo.contract.auth.LoginRequestDto
import com.anksoft.kmpdemo.contract.auth.RefreshTokenRequestDto
import com.anksoft.kmpdemo.contract.auth.RegisterRequestDto
import com.anksoft.kmpdemo.contract.auth.UserDto
import com.anksoft.kmpdemo.contract.error.ErrorResponseDto
import kotlinx.serialization.json.Json
import kotlin.test.Test

/**
 * Golden JSON strings guard the wire format. Changing one of them is a contract
 * change and must be justified in review (ADR-9).
 */
class WireFormatTest {

    private val json = Json { explicitNulls = false }
    private val clientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    private inline fun <reified T> check(sample: T, golden: String) {
        assertThat(json.encodeToString(sample)).isEqualTo(golden)
        assertThat(json.decodeFromString<T>(golden)).isEqualTo(sample)
    }

    @Test
    fun `login request wire format is stable`() =
        check(LoginRequestDto("a@b.com", "Secret123"), """{"email":"a@b.com","password":"Secret123"}""")

    @Test
    fun `register request wire format is stable`() =
        check(RegisterRequestDto("a@b.com", "Secret123"), """{"email":"a@b.com","password":"Secret123"}""")

    @Test
    fun `refresh request wire format is stable`() =
        check(RefreshTokenRequestDto("rt"), """{"refresh_token":"rt"}""")

    @Test
    fun `auth response wire format is stable`() =
        check(
            AuthResponseDto("at", "rt", UserDto("id-1", "a@b.com", "Ali")),
            """{"access_token":"at","refresh_token":"rt","user":{"id":"id-1","email":"a@b.com","name":"Ali"}}""",
        )

    @Test
    fun `error response wire format is stable`() =
        check(
            ErrorResponseDto("invalid_credentials", "Invalid email or password."),
            """{"error":"invalid_credentials","message":"Invalid email or password."}""",
        )

    @Test
    fun `user without name omits the name field`() {
        val encoded = json.encodeToString(UserDto("id-1", "a@b.com"))
        assertThat(encoded).isEqualTo("""{"id":"id-1","email":"a@b.com"}""")
        assertThat(encoded).doesNotContain("name")
    }

    @Test
    fun `client json decodes payloads with unknown fields and missing optionals`() {
        val decoded = clientJson.decodeFromString<AuthResponseDto>(
            """{"access_token":"at","extra":1,"user":{"id":"1","email":"a@b.com","future":true}}""",
        )
        assertThat(decoded.refreshToken).isNull()
        assertThat(decoded.user.name).isNull()
    }
}

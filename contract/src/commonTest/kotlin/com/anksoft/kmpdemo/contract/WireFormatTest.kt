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
import com.anksoft.kmpdemo.contract.consent.AccountConsentDto
import com.anksoft.kmpdemo.contract.consent.ConsentDecisionDto
import com.anksoft.kmpdemo.contract.consent.ConsentTextsDto
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

    // --- Consent additions (contract 0.2.0); the goldens above stay untouched. ---

    @Test
    fun `register request with consent wire format is stable`() =
        check(
            RegisterRequestDto("a@b.com", "Secret123", ConsentDecisionDto("granted", 1, "tr")),
            """{"email":"a@b.com","password":"Secret123","consent":{"status":"granted","text_version":1,"text_language":"tr"}}""",
        )

    @Test
    fun `consent decision wire format is stable`() =
        check(ConsentDecisionDto("denied", 1, "en"), """{"status":"denied","text_version":1,"text_language":"en"}""")

    @Test
    fun `consent texts wire format is stable`() =
        check(
            ConsentTextsDto(1, "tr", "L", "D", "https://example.com/privacy"),
            """{"version":1,"language":"tr","label":"L","description":"D","policy_url":"https://example.com/privacy"}""",
        )

    @Test
    fun `account consent decided wire format is stable`() =
        check(
            AccountConsentDto("granted", 1, "tr", "2026-10-08T12:00:00Z", false),
            """{"status":"granted","text_version":1,"text_language":"tr","decided_at":"2026-10-08T12:00:00Z","reconsent_required":false}""",
        )

    @Test
    fun `account consent none wire format is stable`() =
        check(AccountConsentDto("none", reconsentRequired = false), """{"status":"none","reconsent_required":false}""")

    @Test
    fun `auth response with consent wire format is stable`() =
        check(
            AuthResponseDto("at", "rt", UserDto("id-1", "a@b.com"), AccountConsentDto("none", reconsentRequired = false)),
            """{"access_token":"at","refresh_token":"rt","user":{"id":"id-1","email":"a@b.com"},"consent":{"status":"none","reconsent_required":false}}""",
        )

    @Test
    fun `register request without consent omits the field`() {
        val encoded = json.encodeToString(RegisterRequestDto("a@b.com", "Secret123"))
        assertThat(encoded).isEqualTo("""{"email":"a@b.com","password":"Secret123"}""")
        assertThat(encoded).doesNotContain("consent")
    }

    @Test
    fun `client json tolerates an unknown consent status`() {
        val decoded = clientJson.decodeFromString<AccountConsentDto>(
            """{"status":"withdrawn","reconsent_required":false,"future":1}""",
        )
        assertThat(decoded.status).isEqualTo("withdrawn")
    }

    @Test
    fun `old auth response decodes with the new dto`() {
        val decoded = clientJson.decodeFromString<AuthResponseDto>(
            """{"access_token":"at","user":{"id":"1","email":"a@b.com"}}""",
        )
        assertThat(decoded.consent).isNull()
    }
}

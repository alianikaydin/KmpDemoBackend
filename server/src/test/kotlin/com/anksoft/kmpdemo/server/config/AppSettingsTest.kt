package com.anksoft.kmpdemo.server.config

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.messageContains
import assertk.assertions.isTrue
import java.time.Duration
import kotlin.test.Test

class AppSettingsTest {

    private val secret = "0123456789abcdef0123456789abcdef"

    private val minimalEnv = mapOf(
        "DATABASE_URL" to "jdbc:postgresql://db:5432/kmpdemo",
        "DATABASE_USER" to "kmpdemo",
        "DATABASE_PASSWORD" to "db-password-value",
        "JWT_SECRET" to secret,
    )

    @Test
    fun `defaults are applied when only required variables are set`() { // AC-12
        val settings = AppSettings.fromEnv(minimalEnv)

        assertThat(settings.port).isEqualTo(8081)
        assertThat(settings.accessTokenTtl).isEqualTo(Duration.ofMinutes(15))
        assertThat(settings.refreshTokenTtl).isEqualTo(Duration.ofDays(30))
        assertThat(settings.rateLimitAuthPerMinute).isEqualTo(20)
        assertThat(settings.dbMigrateOnStart).isTrue()
        assertThat(settings.swaggerEnabled).isFalse()
        assertThat(settings.corsAllowedOrigins).isEqualTo(emptyList())
    }

    @Test
    fun `cors origins are split and trimmed`() {
        val settings = AppSettings.fromEnv(minimalEnv + ("CORS_ALLOWED_ORIGINS" to " http://a.test , http://b.test ,"))
        assertThat(settings.corsAllowedOrigins).isEqualTo(listOf("http://a.test", "http://b.test"))
    }

    @Test
    fun `missing jwt secret fails and names the variable`() {
        assertFailure { AppSettings.fromEnv(minimalEnv - "JWT_SECRET") }
            .messageContains("JWT_SECRET")
    }

    @Test
    fun `jwt secret shorter than 32 bytes fails without echoing the value`() {
        val short = "a".repeat(31)
        val failure = runCatching { AppSettings.fromEnv(minimalEnv + ("JWT_SECRET" to short)) }.exceptionOrNull()
        assertThat(failure?.message.orEmpty()).contains("JWT_SECRET")
        assertThat(failure?.message.orEmpty()).doesNotContain(short)
    }

    @Test
    fun `invalid access token ttl fails`() {
        assertFailure { AppSettings.fromEnv(minimalEnv + ("ACCESS_TOKEN_TTL" to "15 minutes")) }
            .messageContains("ACCESS_TOKEN_TTL")
    }

    @Test
    fun `missing database url fails`() {
        assertFailure { AppSettings.fromEnv(minimalEnv - "DATABASE_URL") }
            .messageContains("DATABASE_URL")
    }

    @Test
    fun `invalid port fails`() {
        assertFailure { AppSettings.fromEnv(minimalEnv + ("PORT" to "70000")) }
            .messageContains("PORT")
    }

    @Test
    fun `toString never contains secrets`() { // AC-11
        val text = AppSettings.fromEnv(minimalEnv).toString()

        assertThat(text).doesNotContain(secret)
        assertThat(text).doesNotContain("db-password-value")
        assertThat(text).contains("jwtSecret=<masked>")
    }
}

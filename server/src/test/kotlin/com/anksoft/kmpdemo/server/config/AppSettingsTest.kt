package com.anksoft.kmpdemo.server.config

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.messageContains
import assertk.assertions.isTrue
import com.anksoft.kmpdemo.server.appversion.domain.AppVersion
import com.anksoft.kmpdemo.server.appversion.domain.VersionPolicy
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

    // Version policy (E8, ADR-17)

    private val platforms = listOf("ANDROID", "IOS")

    private fun policyOf(settings: AppSettings, platform: String): VersionPolicy =
        if (platform == "ANDROID") settings.androidVersionPolicy else settings.iosVersionPolicy

    @Test
    fun `version policies default to no update when no variable is set`() { // AC-27
        val settings = AppSettings.fromEnv(minimalEnv)

        assertThat(settings.androidVersionPolicy).isEqualTo(VersionPolicy.NO_UPDATE)
        assertThat(settings.iosVersionPolicy).isEqualTo(VersionPolicy.NO_UPDATE)
    }

    @Test
    fun `valid version values are read per platform and trimmed`() { // AC-18
        val settings = AppSettings.fromEnv(
            minimalEnv + mapOf(
                "APP_ANDROID_MIN_SUPPORTED_VERSION" to " 1.0.0 ",
                "APP_ANDROID_LATEST_VERSION" to "1.2.0",
                "APP_IOS_MIN_SUPPORTED_VERSION" to "2.1.0",
                "APP_IOS_LATEST_VERSION" to "3.0.1",
            ),
        )

        assertThat(settings.androidVersionPolicy)
            .isEqualTo(VersionPolicy(AppVersion(1, 0, 0), AppVersion(1, 2, 0)))
        assertThat(settings.iosVersionPolicy)
            .isEqualTo(VersionPolicy(AppVersion(2, 1, 0), AppVersion(3, 0, 1)))
    }

    @Test
    fun `min equal to latest is accepted`() {
        platforms.forEach { platform ->
            val settings = AppSettings.fromEnv(
                minimalEnv + mapOf(
                    "APP_${platform}_MIN_SUPPORTED_VERSION" to "1.4.0",
                    "APP_${platform}_LATEST_VERSION" to "1.4.0",
                ),
            )
            assertThat(policyOf(settings, platform)).isEqualTo(VersionPolicy(AppVersion(1, 4, 0), AppVersion(1, 4, 0)))
        }
    }

    @Test
    fun `blank version values fall back to the default`() {
        platforms.forEach { platform ->
            val settings = AppSettings.fromEnv(
                minimalEnv + mapOf(
                    "APP_${platform}_MIN_SUPPORTED_VERSION" to "",
                    "APP_${platform}_LATEST_VERSION" to "   ",
                ),
            )
            assertThat(policyOf(settings, platform)).isEqualTo(VersionPolicy.NO_UPDATE)
        }
    }

    @Test
    fun `invalid version format fails naming the variable but not its value`() { // R3
        platforms.forEach { platform ->
            listOf("MIN_SUPPORTED", "LATEST").forEach { kind ->
                val name = "APP_${platform}_${kind}_VERSION"
                listOf("v1.2.3", "1.2", "1.2.3.4", "01.2.3", "1.2.3-beta", "latest").forEach { bad ->
                    val failure = runCatching { AppSettings.fromEnv(minimalEnv + (name to bad)) }.exceptionOrNull()
                    assertThat(failure?.message.orEmpty(), "$name=$bad").contains(name)
                    assertThat(failure?.message.orEmpty(), "$name=$bad").doesNotContain(bad)
                }
            }
        }
    }

    @Test
    fun `min above latest fails naming the min variable`() { // R3
        platforms.forEach { platform ->
            val failure = runCatching {
                AppSettings.fromEnv(
                    minimalEnv + mapOf(
                        "APP_${platform}_MIN_SUPPORTED_VERSION" to "2.0.0",
                        "APP_${platform}_LATEST_VERSION" to "1.9.9",
                    ),
                )
            }.exceptionOrNull()
            assertThat(failure?.message.orEmpty()).contains("APP_${platform}_MIN_SUPPORTED_VERSION")
            assertThat(failure?.message.orEmpty()).doesNotContain("2.0.0")
        }
    }

    @Test
    fun `min without latest fails because latest defaults to zero`() { // R3
        platforms.forEach { platform ->
            assertFailure {
                AppSettings.fromEnv(minimalEnv + ("APP_${platform}_MIN_SUPPORTED_VERSION" to "2.0.0"))
            }.messageContains("APP_${platform}_MIN_SUPPORTED_VERSION")
        }
    }

    @Test
    fun `toString shows the effective version policies`() { // ADR-17
        val text = AppSettings.fromEnv(
            minimalEnv + mapOf(
                "APP_ANDROID_MIN_SUPPORTED_VERSION" to "1.0.0",
                "APP_ANDROID_LATEST_VERSION" to "1.2.0",
            ),
        ).toString()

        assertThat(text).contains("androidVersionPolicy=VersionPolicy(minSupported=1.0.0, latest=1.2.0)")
        assertThat(text).contains("iosVersionPolicy=VersionPolicy(minSupported=0.0.0, latest=0.0.0)")
    }
}

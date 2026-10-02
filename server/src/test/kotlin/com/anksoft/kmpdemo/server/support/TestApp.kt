package com.anksoft.kmpdemo.server.support

import com.anksoft.kmpdemo.server.config.AppSettings
import com.anksoft.kmpdemo.server.module
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Duration

const val TEST_JWT_SECRET = "test-secret-test-secret-test-secret-0123456789"

/** Mirrors the app client's JSON settings so tests decode responses the way a real client does. */
val clientJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun testSettings(transform: (AppSettings) -> AppSettings = { it }): AppSettings = transform(
    AppSettings(
        port = 0,
        databaseUrl = "jdbc:postgresql://localhost:5432/unused",
        databaseUser = "unused",
        databasePassword = "unused",
        databasePoolSize = 4,
        dbMigrateOnStart = true,
        jwtSecret = TEST_JWT_SECRET,
        jwtIssuer = "kmp-demo-server",
        jwtAudience = "kmp-demo-app",
        accessTokenTtl = Duration.ofMinutes(15),
        refreshTokenTtl = Duration.ofDays(30),
        corsAllowedOrigins = listOf("http://localhost:8080"),
        rateLimitAuthPerMinute = 0,
        jsonLogs = false,
        swaggerEnabled = false,
    ),
)

fun withTestApp(
    settings: AppSettings = testSettings(),
    clock: Clock = Clock.systemUTC(),
    extra: Application.() -> Unit = {},
    block: suspend ApplicationTestBuilder.(HttpClient) -> Unit,
) = testApplication {
    application {
        module(settings, clock)
        extra()
    }
    val client = createClient { install(ContentNegotiation) { json(clientJson) } }
    block(client)
}

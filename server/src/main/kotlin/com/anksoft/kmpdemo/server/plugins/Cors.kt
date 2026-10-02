package com.anksoft.kmpdemo.server.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

/** Only the configured origins are allowed; with an empty list CORS is not installed at all. */
fun Application.configureCors(allowedOrigins: List<String>) {
    if (allowedOrigins.isEmpty()) return
    val allowed = allowedOrigins.toSet()
    install(CORS) {
        allowOrigins { it in allowed }
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowCredentials = false
        maxAgeInSeconds = 600
    }
}

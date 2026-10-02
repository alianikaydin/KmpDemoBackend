package com.anksoft.kmpdemo.server.plugins

import com.anksoft.kmpdemo.server.config.AppSettings
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.Route
import kotlin.time.Duration.Companion.seconds

val AUTH_RATE_LIMIT = RateLimitName("auth")

/** Per-instance, per-client-IP limit for the auth endpoints; a limit of 0 disables it (risk R4). */
fun Application.configureRateLimiting(settings: AppSettings) {
    if (settings.rateLimitAuthPerMinute <= 0) return
    install(RateLimit) {
        register(AUTH_RATE_LIMIT) {
            rateLimiter(limit = settings.rateLimitAuthPerMinute, refillPeriod = 60.seconds)
            requestKey { call -> call.request.origin.remoteHost }
        }
    }
}

/** Wraps [build] in the auth rate limit when enabled, otherwise registers the routes as they are. */
fun Route.authRateLimited(enabled: Boolean, build: Route.() -> Unit) {
    if (enabled) rateLimit(AUTH_RATE_LIMIT, build) else build()
}

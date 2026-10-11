package com.anksoft.kmpdemo.server.appversion.routes

import com.anksoft.kmpdemo.contract.app.VersionPolicyPaths
import com.anksoft.kmpdemo.contract.auth.AuthPaths
import com.anksoft.kmpdemo.contract.error.ErrorCodes
import com.anksoft.kmpdemo.server.appversion.service.VersionPolicyService
import com.anksoft.kmpdemo.server.plugins.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

private const val NO_STORE = "no-store"

/**
 * HTTP translation only. The route is public and never reads the Authorization header, so it cannot
 * answer 401; it is outside the auth rate limit because every cold start calls it (ADR-16).
 */
fun Route.versionPolicyRoutes(service: VersionPolicyService) {
    route(AuthPaths.API_PREFIX.trimEnd('/')) {
        get(VersionPolicyPaths.VERSION_POLICY) {
            call.response.header(HttpHeaders.CacheControl, NO_STORE)
            val policy = service.policyFor(call.request.queryParameters[VersionPolicyPaths.PLATFORM_PARAM])
            if (policy == null) {
                call.respondError(HttpStatusCode.BadRequest, ErrorCodes.INVALID_REQUEST)
            } else {
                call.respond(policy.toDto())
            }
        }
    }
}

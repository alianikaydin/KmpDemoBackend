package com.anksoft.kmpdemo.server.consent.routes

import com.anksoft.kmpdemo.contract.auth.AuthPaths
import com.anksoft.kmpdemo.contract.consent.ConsentDecisionDto
import com.anksoft.kmpdemo.contract.consent.ConsentPaths
import com.anksoft.kmpdemo.contract.error.ErrorCodes
import com.anksoft.kmpdemo.server.consent.domain.ConsentError
import com.anksoft.kmpdemo.server.consent.domain.ConsentResult
import com.anksoft.kmpdemo.server.consent.service.ConsentService
import com.anksoft.kmpdemo.server.plugins.JWT_AUTH
import com.anksoft.kmpdemo.server.plugins.UserPrincipal
import com.anksoft.kmpdemo.server.plugins.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

private const val TEXTS_CACHE_CONTROL = "max-age=300"

/**
 * HTTP translation only; every rule lives in [ConsentService]. These routes are deliberately
 * outside the auth rate limit (ADR-15): reading texts is cheap and account/consent needs a JWT.
 */
fun Route.consentRoutes(service: ConsentService) {
    route(AuthPaths.API_PREFIX.trimEnd('/')) {
        get(ConsentPaths.TEXTS) {
            val texts = service.currentTexts(call.request.queryParameters[ConsentPaths.LANG_PARAM])
            call.response.header(HttpHeaders.CacheControl, TEXTS_CACHE_CONTROL)
            call.respond(texts.toDto())
        }
        authenticate(JWT_AUTH) {
            get(ConsentPaths.ACCOUNT_CONSENT) {
                val principal = checkNotNull(call.principal<UserPrincipal>())
                call.respond(service.accountConsent(principal.userId).toDto())
            }
            put(ConsentPaths.ACCOUNT_CONSENT) {
                val principal = checkNotNull(call.principal<UserPrincipal>())
                val request = call.receive<ConsentDecisionDto>()
                when (val result = service.updateConsent(principal.userId, request.toInput())) {
                    is ConsentResult.Ok -> call.respond(result.value.toDto())
                    is ConsentResult.Err -> call.respondConsentError(result.error)
                }
            }
        }
    }
}

private suspend fun ApplicationCall.respondConsentError(error: ConsentError) = when (error) {
    ConsentError.INVALID_INPUT -> respondError(HttpStatusCode.BadRequest, ErrorCodes.INVALID_REQUEST)
    ConsentError.UNKNOWN_TEXT_VERSION ->
        respondError(HttpStatusCode.UnprocessableEntity, ErrorCodes.UNKNOWN_CONSENT_VERSION)
    ConsentError.ACCOUNT_NOT_FOUND -> {
        response.header(HttpHeaders.WWWAuthenticate, "Bearer")
        respondError(HttpStatusCode.Unauthorized, ErrorCodes.INVALID_TOKEN)
    }
}

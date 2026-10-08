package com.anksoft.kmpdemo.server.auth.routes

import com.anksoft.kmpdemo.contract.auth.AuthPaths
import com.anksoft.kmpdemo.contract.auth.LoginRequestDto
import com.anksoft.kmpdemo.contract.auth.RefreshTokenRequestDto
import com.anksoft.kmpdemo.contract.auth.RegisterRequestDto
import com.anksoft.kmpdemo.contract.error.ErrorCodes
import com.anksoft.kmpdemo.server.auth.domain.AuthError
import com.anksoft.kmpdemo.server.auth.domain.AuthResult
import com.anksoft.kmpdemo.server.auth.service.AuthService
import com.anksoft.kmpdemo.server.plugins.JWT_AUTH
import com.anksoft.kmpdemo.server.plugins.UserPrincipal
import com.anksoft.kmpdemo.server.plugins.authRateLimited
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
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/** HTTP translation only; every business rule lives in [AuthService]. */
fun Route.authRoutes(service: AuthService, rateLimited: Boolean) {
    route("api/v1") {
        authRateLimited(rateLimited) {
            post(AuthPaths.REGISTER) {
                val request = call.receive<RegisterRequestDto>()
                when (val result = service.register(request.email, request.password)) {
                    is AuthResult.Ok -> call.respond(result.value.toDto())
                    is AuthResult.Err -> call.respondAuthError(result.error)
                }
            }
            post(AuthPaths.LOGIN) {
                val request = call.receive<LoginRequestDto>()
                when (val result = service.login(request.email, request.password)) {
                    is AuthResult.Ok -> call.respond(result.value.toDto())
                    is AuthResult.Err -> call.respondAuthError(result.error)
                }
            }
            post(AuthPaths.REFRESH) {
                val request = call.receive<RefreshTokenRequestDto>()
                when (val result = service.refresh(request.refreshToken)) {
                    is AuthResult.Ok -> call.respond(result.value.toDto())
                    is AuthResult.Err -> call.respondAuthError(result.error)
                }
            }
            post(AuthPaths.LOGOUT) {
                val request = call.receive<RefreshTokenRequestDto>()
                when (val result = service.logout(request.refreshToken)) {
                    is AuthResult.Ok -> call.respond(HttpStatusCode.NoContent)
                    is AuthResult.Err -> call.respondAuthError(result.error)
                }
            }
        }
        authenticate(JWT_AUTH) {
            get(AuthPaths.ME) {
                val principal = checkNotNull(call.principal<UserPrincipal>())
                when (val result = service.currentUser(principal.userId)) {
                    is AuthResult.Ok -> call.respond(result.value.toDto())
                    is AuthResult.Err -> {
                        call.response.header(HttpHeaders.WWWAuthenticate, "Bearer")
                        call.respondAuthError(result.error)
                    }
                }
            }
        }
    }
}

private suspend fun ApplicationCall.respondAuthError(error: AuthError) = when (error) {
    AuthError.INVALID_INPUT -> respondError(HttpStatusCode.BadRequest, ErrorCodes.INVALID_REQUEST)
    AuthError.EMAIL_TAKEN -> respondError(HttpStatusCode.Conflict, ErrorCodes.EMAIL_TAKEN)
    AuthError.INVALID_CREDENTIALS -> respondError(HttpStatusCode.Unauthorized, ErrorCodes.INVALID_CREDENTIALS)
    AuthError.INVALID_TOKEN -> respondError(HttpStatusCode.Unauthorized, ErrorCodes.INVALID_TOKEN)
    AuthError.UNKNOWN_CONSENT_VERSION ->
        respondError(HttpStatusCode.UnprocessableEntity, ErrorCodes.UNKNOWN_CONSENT_VERSION)
}

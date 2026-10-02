package com.anksoft.kmpdemo.server.plugins

import com.anksoft.kmpdemo.contract.error.ErrorCodes
import com.anksoft.kmpdemo.server.auth.security.JwtConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.header
import java.util.UUID

const val JWT_AUTH = "access"

/** Authenticated caller, taken from the JWT subject; no database lookup happens during authentication. */
data class UserPrincipal(val userId: UUID)

fun Application.configureSecurity(jwtConfig: JwtConfig) {
    install(Authentication) {
        jwt(JWT_AUTH) {
            verifier(jwtConfig.verifier())
            validate { credential ->
                credential.payload.subject
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?.let(::UserPrincipal)
            }
            challenge { _, _ ->
                call.response.header(HttpHeaders.WWWAuthenticate, "Bearer")
                call.respondError(HttpStatusCode.Unauthorized, ErrorCodes.INVALID_TOKEN)
            }
        }
    }
}

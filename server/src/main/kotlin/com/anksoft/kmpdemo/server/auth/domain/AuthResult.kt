package com.anksoft.kmpdemo.server.auth.domain

enum class AuthError { INVALID_INPUT, EMAIL_TAKEN, INVALID_CREDENTIALS, INVALID_TOKEN, UNKNOWN_CONSENT_VERSION }

sealed interface AuthResult<out T> {
    data class Ok<out T>(val value: T) : AuthResult<T>
    data class Err(val error: AuthError) : AuthResult<Nothing>
}

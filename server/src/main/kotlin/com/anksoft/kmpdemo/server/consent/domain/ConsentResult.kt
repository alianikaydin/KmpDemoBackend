package com.anksoft.kmpdemo.server.consent.domain

enum class ConsentError { INVALID_INPUT, UNKNOWN_TEXT_VERSION, ACCOUNT_NOT_FOUND }

sealed interface ConsentResult<out T> {
    data class Ok<out T>(val value: T) : ConsentResult<T>
    data class Err(val error: ConsentError) : ConsentResult<Nothing>
}

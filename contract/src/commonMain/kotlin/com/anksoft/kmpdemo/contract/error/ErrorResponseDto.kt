package com.anksoft.kmpdemo.contract.error

import kotlinx.serialization.Serializable

/** Body of every 4xx/5xx response. [error] is a machine-readable code from [ErrorCodes]. */
@Serializable
public data class ErrorResponseDto(
    val error: String,
    val message: String,
)

public object ErrorCodes {
    public const val INVALID_REQUEST: String = "invalid_request"
    public const val INVALID_CREDENTIALS: String = "invalid_credentials"
    public const val INVALID_TOKEN: String = "invalid_token"
    public const val EMAIL_TAKEN: String = "email_taken"
    public const val RATE_LIMITED: String = "rate_limited"
    public const val PAYLOAD_TOO_LARGE: String = "payload_too_large"
    public const val UNSUPPORTED_MEDIA_TYPE: String = "unsupported_media_type"
    public const val NOT_FOUND: String = "not_found"
    public const val METHOD_NOT_ALLOWED: String = "method_not_allowed"
    public const val INTERNAL_ERROR: String = "internal_error"
}

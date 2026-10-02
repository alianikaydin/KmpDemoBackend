package com.anksoft.kmpdemo.server.plugins

import com.anksoft.kmpdemo.contract.error.ErrorCodes
import com.anksoft.kmpdemo.contract.error.ErrorResponseDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.CannotTransformContentToTypeException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.ContentTransformationException
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory
import java.sql.SQLException

private val log = LoggerFactory.getLogger("com.anksoft.kmpdemo.server.errors")

private val defaultMessages = mapOf(
    ErrorCodes.INVALID_REQUEST to "Invalid request.",
    ErrorCodes.INVALID_CREDENTIALS to "Invalid email or password.",
    ErrorCodes.INVALID_TOKEN to "Invalid or expired token.",
    ErrorCodes.EMAIL_TAKEN to "Email is already registered.",
    ErrorCodes.RATE_LIMITED to "Too many requests.",
    ErrorCodes.PAYLOAD_TOO_LARGE to "Request body too large.",
    ErrorCodes.UNSUPPORTED_MEDIA_TYPE to "Unsupported media type.",
    ErrorCodes.NOT_FOUND to "Not found.",
    ErrorCodes.METHOD_NOT_ALLOWED to "Method not allowed.",
    ErrorCodes.INTERNAL_ERROR to "Internal server error.",
)

/** Responds with the standard [ErrorResponseDto] body for [code]; the message is a fixed constant. */
suspend fun ApplicationCall.respondError(status: HttpStatusCode, code: String) {
    respond(status, ErrorResponseDto(error = code, message = defaultMessages.getValue(code)))
}

/**
 * Safe description of a throwable for logs: class name, SQL state and a few
 * frames. The message is deliberately omitted because it can carry SQL
 * parameters or request body fragments (ADR-11).
 */
internal fun safeDescription(cause: Throwable): String = buildString {
    append(cause::class.qualifiedName)
    if (cause is SQLException) append(" sqlState=").append(cause.sqlState)
    cause.stackTrace.take(6).forEach { append("\n\tat ").append(it) }
}

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<PayloadTooLargeException> { call, _ ->
            call.respondError(HttpStatusCode.PayloadTooLarge, ErrorCodes.PAYLOAD_TOO_LARGE)
        }
        exception<UnsupportedMediaTypeException> { call, _ ->
            call.respondError(HttpStatusCode.UnsupportedMediaType, ErrorCodes.UNSUPPORTED_MEDIA_TYPE)
        }
        // Thrown when no converter accepts the request content type (e.g. text/plain or a missing header).
        exception<CannotTransformContentToTypeException> { call, _ ->
            call.respondError(HttpStatusCode.UnsupportedMediaType, ErrorCodes.UNSUPPORTED_MEDIA_TYPE)
        }
        exception<BadRequestException> { call, cause ->
            log.info("Bad request: {}", cause.cause?.let { it::class.qualifiedName } ?: cause::class.qualifiedName)
            call.respondError(HttpStatusCode.BadRequest, ErrorCodes.INVALID_REQUEST)
        }
        exception<ContentTransformationException> { call, cause ->
            log.info("Bad request: {}", cause::class.qualifiedName)
            call.respondError(HttpStatusCode.BadRequest, ErrorCodes.INVALID_REQUEST)
        }
        exception<Throwable> { call, cause ->
            log.error("Unhandled exception: {}", safeDescription(cause))
            call.respondError(HttpStatusCode.InternalServerError, ErrorCodes.INTERNAL_ERROR)
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respondError(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND)
        }
        status(HttpStatusCode.TooManyRequests) { call, _ ->
            call.respondError(HttpStatusCode.TooManyRequests, ErrorCodes.RATE_LIMITED)
        }
        status(HttpStatusCode.MethodNotAllowed) { call, _ ->
            call.respondError(HttpStatusCode.MethodNotAllowed, ErrorCodes.METHOD_NOT_ALLOWED)
        }
    }
}

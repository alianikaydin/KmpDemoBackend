package com.anksoft.kmpdemo.server.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.slf4j.event.Level
import java.util.UUID

const val REQUEST_ID_HEADER = "X-Request-Id"
const val MAX_BODY_BYTES = 16L * 1024

private val SAFE_REQUEST_ID = Regex("^[A-Za-z0-9._-]{1,100}$")

/**
 * Request id, call logging and body size limit. Call logging records only
 * method, path, status and duration: never bodies, headers, query strings,
 * passwords or tokens (ADR-11).
 */
fun Application.configureHttp() {
    install(CallId) {
        retrieveFromHeader(REQUEST_ID_HEADER)
        generate { UUID.randomUUID().toString() }
        verify { SAFE_REQUEST_ID.matches(it) }
        replyToHeader(REQUEST_ID_HEADER)
    }
    install(CallLogging) {
        level = Level.INFO
        callIdMdc("call_id")
        format { call ->
            "${call.request.httpMethod.value} ${call.request.path()} -> " +
                "${call.response.status()?.value} in ${call.processingTimeMillis()}ms"
        }
    }
    install(RequestBodyLimit) {
        bodyLimit { MAX_BODY_BYTES }
    }
}

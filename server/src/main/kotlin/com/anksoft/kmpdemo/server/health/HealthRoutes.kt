package com.anksoft.kmpdemo.server.health

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

@Serializable
data class HealthDto(val status: String)

/** `live` only checks that the process answers; `ready` also runs a database probe. */
fun Route.healthRoutes(databaseReady: suspend () -> Boolean) {
    route("health") {
        get("live") { call.respond(HealthDto("UP")) }
        // Readiness reflects the database so a load balancer stops sending traffic to an instance without one.
        get("ready") {
            if (databaseReady()) {
                call.respond(HealthDto("UP"))
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable, HealthDto("DOWN"))
            }
        }
    }
}

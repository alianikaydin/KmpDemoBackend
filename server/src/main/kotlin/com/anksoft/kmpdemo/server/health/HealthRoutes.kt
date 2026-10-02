package com.anksoft.kmpdemo.server.health

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

@Serializable
data class HealthDto(val status: String)

/** Liveness only checks that the process answers; it never touches the database. */
fun Route.healthRoutes() {
    route("health") {
        get("live") { call.respond(HealthDto("UP")) }
    }
}

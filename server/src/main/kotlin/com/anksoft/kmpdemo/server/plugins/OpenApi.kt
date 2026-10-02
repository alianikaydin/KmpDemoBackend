package com.anksoft.kmpdemo.server.plugins

import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.Route

/** Serves Swagger UI for the hand-written OpenAPI document at `/swagger`. */
fun Route.openApiRoutes(enabled: Boolean) {
    if (enabled) swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")
}

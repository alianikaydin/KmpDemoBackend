package com.anksoft.kmpdemo.server

import com.anksoft.kmpdemo.server.config.AppSettings
import com.anksoft.kmpdemo.server.health.healthRoutes
import com.anksoft.kmpdemo.server.plugins.configureHttp
import com.anksoft.kmpdemo.server.plugins.configureSerialization
import com.anksoft.kmpdemo.server.plugins.configureStatusPages
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import java.time.Clock

fun main() {
    val settings = AppSettings.fromEnv(System.getenv())
    embeddedServer(Netty, port = settings.port, host = "0.0.0.0") {
        module(settings)
    }.start(wait = true)
}

fun Application.module(settings: AppSettings, clock: Clock = Clock.systemUTC()) {
    configureHttp()
    configureSerialization()
    configureStatusPages()
    routing {
        healthRoutes()
    }
}

package com.anksoft.kmpdemo.server

import com.anksoft.kmpdemo.server.auth.routes.authRoutes
import com.anksoft.kmpdemo.server.auth.security.JwtConfig
import com.anksoft.kmpdemo.server.auth.service.AuthService
import com.anksoft.kmpdemo.server.config.AppSettings
import com.anksoft.kmpdemo.server.consent.routes.consentRoutes
import com.anksoft.kmpdemo.server.consent.service.ConsentService
import com.anksoft.kmpdemo.server.di.serverModule
import com.anksoft.kmpdemo.server.db.connect
import com.anksoft.kmpdemo.server.db.createDataSource
import com.anksoft.kmpdemo.server.db.migrate
import com.anksoft.kmpdemo.server.db.ping
import com.anksoft.kmpdemo.server.health.healthRoutes
import com.anksoft.kmpdemo.server.plugins.configureCors
import com.anksoft.kmpdemo.server.plugins.configureHttp
import com.anksoft.kmpdemo.server.plugins.configureRateLimiting
import com.anksoft.kmpdemo.server.plugins.openApiRoutes
import com.anksoft.kmpdemo.server.plugins.configureSecurity
import com.anksoft.kmpdemo.server.plugins.configureSerialization
import com.anksoft.kmpdemo.server.plugins.configureStatusPages
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import kotlinx.coroutines.runBlocking
import org.koin.core.module.Module
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.KoinIsolated
import org.koin.logger.slf4jLogger
import java.time.Clock

fun main() {
    val settings = AppSettings.fromEnv(System.getenv())
    embeddedServer(Netty, port = settings.port, host = "0.0.0.0") {
        module(settings)
    }.start(wait = true)
}

/**
 * Wires the application. [extraModules] are Koin modules loaded after the
 * defaults; tests use them to replace single components.
 */
fun Application.module(
    settings: AppSettings,
    clock: Clock = Clock.systemUTC(),
    extraModules: List<Module> = emptyList(),
) {
    val dataSource = createDataSource(settings)
    val db: Database
    val authService: AuthService
    val consentService: ConsentService
    try {
        if (settings.dbMigrateOnStart) migrate(dataSource)
        db = connect(dataSource)
        install(KoinIsolated) {
            slf4jLogger()
            modules(listOf(serverModule(settings, db, clock)) + extraModules)
        }
        authService = get<AuthService>()
        consentService = get<ConsentService>()
        runBlocking {
            authService.warmUp()
            consentService.warnIfPlaceholderTexts()
        }
    } catch (e: Exception) {
        // Startup failed after the pool was opened: release it instead of leaking connections.
        dataSource.close()
        throw e
    }
    monitor.subscribe(ApplicationStopped) {
        TransactionManager.closeAndUnregister(db)
        dataSource.close()
    }

    configureHttp()
    configureSerialization()
    configureStatusPages()
    configureCors(settings.corsAllowedOrigins)
    configureRateLimiting(settings)
    configureSecurity(get<JwtConfig>())
    routing {
        healthRoutes(databaseReady = { db.ping() })
        authRoutes(authService, rateLimited = settings.rateLimitAuthPerMinute > 0)
        consentRoutes(consentService)
        openApiRoutes(settings.swaggerEnabled)
    }
}

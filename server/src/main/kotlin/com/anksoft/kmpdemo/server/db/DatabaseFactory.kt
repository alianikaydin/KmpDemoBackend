package com.anksoft.kmpdemo.server.db

import com.anksoft.kmpdemo.server.config.AppSettings
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import javax.sql.DataSource
import kotlin.time.Duration.Companion.seconds

fun createDataSource(settings: AppSettings): HikariDataSource = HikariDataSource(
    HikariConfig().apply {
        jdbcUrl = settings.databaseUrl
        username = settings.databaseUser
        password = settings.databasePassword
        maximumPoolSize = settings.databasePoolSize
        connectionTimeout = 3_000
        validationTimeout = 2_000
        poolName = "server-db"
    },
)

/** Applies pending Flyway migrations; Flyway's own table lock makes concurrent starts safe. */
fun migrate(dataSource: DataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate()
}

/**
 * Exposed retries failed transactions three times by default, which would triple the
 * connection timeout when the database is down; a failed request should fail fast instead.
 */
fun connect(dataSource: DataSource): Database =
    Database.connect(dataSource, databaseConfig = DatabaseConfig { defaultMaxAttempts = 1 })

/** Runs [block] in a transaction on the IO dispatcher against this explicit database. */
suspend fun <T> Database.io(block: JdbcTransaction.() -> T): T =
    withContext(Dispatchers.IO) { transaction(this@io) { block() } }

/** Readiness probe: true when `SELECT 1` succeeds within two seconds. */
suspend fun Database.ping(): Boolean {
    val query = CoroutineScope(Dispatchers.IO).async { transaction(this@ping) { exec("SELECT 1") } }
    return try {
        withTimeout(2.seconds) { query.await() }
        true
    } catch (_: Exception) {
        query.cancel()
        false
    }
}

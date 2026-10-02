package com.anksoft.kmpdemo.server.support

import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.DriverManager

/** One shared Postgres 17 container per test JVM, started lazily; Ryuk removes it on exit. */
object PostgresTestDb {
    private val container: PostgreSQLContainer by lazy {
        PostgreSQLContainer("postgres:17-alpine").also { it.start() }
    }

    val jdbcUrl: String get() = container.jdbcUrl
    val username: String get() = container.username
    val password: String get() = container.password

    /** Empties all tables (the schema is created by the app's own Flyway migration). */
    fun reset() {
        DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
            connection.createStatement().use { statement ->
                val exists = statement.executeQuery("SELECT to_regclass('public.users') IS NOT NULL").use {
                    it.next() && it.getBoolean(1)
                }
                if (exists) statement.execute("TRUNCATE refresh_tokens, users CASCADE")
            }
        }
    }
}

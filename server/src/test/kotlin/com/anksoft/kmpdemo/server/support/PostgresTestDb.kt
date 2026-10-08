package com.anksoft.kmpdemo.server.support

import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.DriverManager

/** One shared Postgres 17 container per test JVM, started lazily; Ryuk removes it on exit. */
const val TEST_TEXT_VERSION_FLOOR = 9000

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
                val consentExists = statement.executeQuery("SELECT to_regclass('public.consent_texts') IS NOT NULL").use {
                    it.next() && it.getBoolean(1)
                }
                if (consentExists) {
                    // Decisions went with users above; drop only the text versions tests added (seed is < 9000).
                    statement.execute("DELETE FROM consent_texts WHERE version >= $TEST_TEXT_VERSION_FLOOR")
                    statement.execute("DELETE FROM consent_text_versions WHERE version >= $TEST_TEXT_VERSION_FLOOR")
                }
            }
        }
    }

    /** Adds a consent text version (>= 9000) with one placeholder text per language; [reset] removes it again. */
    fun insertTextVersion(version: Int, requiresReconsent: Boolean, languages: List<String> = listOf("en", "tr")) {
        require(version >= TEST_TEXT_VERSION_FLOOR) { "test text versions start at $TEST_TEXT_VERSION_FLOOR" }
        execute("INSERT INTO consent_text_versions (version, requires_reconsent) VALUES ($version, $requiresReconsent)")
        languages.forEach { lang ->
            execute(
                "INSERT INTO consent_texts (version, language, label, description, policy_url) " +
                    "VALUES ($version, '$lang', 'label $version $lang', 'description $version $lang', 'https://example.org/p')",
            )
        }
    }

    /** Freezes the database process (docker pause): connections stay open but nothing answers. */
    fun pause() {
        container.dockerClient.pauseContainerCmd(container.containerId).exec()
    }

    fun unpause() {
        container.dockerClient.unpauseContainerCmd(container.containerId).exec()
    }

    fun countRows(table: String): Int = query("SELECT count(*) FROM $table") { it.getInt(1) }

    fun execute(sql: String) {
        DriverManager.getConnection(jdbcUrl, username, password).use { c -> c.createStatement().use { it.execute(sql) } }
    }

    fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T =
        DriverManager.getConnection(jdbcUrl, username, password).use { c ->
            c.createStatement().use { st -> st.executeQuery(sql).use { rs -> check(rs.next()); read(rs) } }
        }
}

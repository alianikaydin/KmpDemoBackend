package com.anksoft.kmpdemo.server.consent.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.anksoft.kmpdemo.server.support.PostgresTestDb
import com.anksoft.kmpdemo.server.support.withTestDatabase
import kotlin.test.Test

class ConsentMigrationTest {

    @Test
    fun `every published text version has an english text`() = withTestDatabase { // AC-24
        val missing = PostgresTestDb.query(
            """
            SELECT count(*) FROM consent_text_versions v
            WHERE NOT EXISTS (SELECT 1 FROM consent_texts t WHERE t.version = v.version AND t.language = 'en')
            """.trimIndent(),
        ) { it.getInt(1) }

        assertThat(missing).isEqualTo(0)
    }

    @Test
    fun `seed version 1 has turkish and english texts`() = withTestDatabase { // AC-21
        val languages = PostgresTestDb.query(
            "SELECT string_agg(language, ',' ORDER BY language) FROM consent_texts WHERE version = 1",
        ) { it.getString(1) }

        assertThat(languages).isEqualTo("en,tr")
    }

    @Test
    fun `seed leaves version 1 as the latest version`() = withTestDatabase { // AC-21
        val latest = PostgresTestDb.query("SELECT max(version) FROM consent_text_versions") { it.getInt(1) }

        assertThat(latest).isEqualTo(1)
    }

    @Test
    fun `seed texts fit the documented length limits`() = withTestDatabase { // AC-21
        val withinLimits = PostgresTestDb.query(
            "SELECT bool_and(length(label) <= 200 AND length(description) <= 2000) FROM consent_texts",
        ) { it.getBoolean(1) }

        assertThat(withinLimits).isTrue()
    }

    @Test
    fun `reset keeps the seed texts and removes test versions`() = withTestDatabase { // AC-21
        PostgresTestDb.insertTextVersion(9001, requiresReconsent = true)
        PostgresTestDb.reset()

        val versions = PostgresTestDb.query("SELECT count(*) FROM consent_text_versions") { it.getInt(1) }
        assertThat(versions).isEqualTo(1)
    }
}

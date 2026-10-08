package com.anksoft.kmpdemo.server.consent.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.anksoft.kmpdemo.server.support.PostgresTestDb
import com.anksoft.kmpdemo.server.support.withTestDatabase
import kotlin.test.Test

class ExposedConsentTextRepositoryTest {

    @Test
    fun `latestVersion returns the seed version and follows newly published versions`() = withTestDatabase { db -> // AC-21
        val repo = ExposedConsentTextRepository(db)
        assertThat(repo.latestVersion()).isEqualTo(1)

        PostgresTestDb.insertTextVersion(9001, requiresReconsent = false)

        assertThat(repo.latestVersion()).isEqualTo(9001)
    }

    @Test
    fun `find returns the seeded text per language and null for unknown pairs`() = withTestDatabase { db -> // AC-21, AC-24
        val repo = ExposedConsentTextRepository(db)

        val tr = repo.find(1, "tr")
        assertThat(tr).isNotNull()
        assertThat(tr!!.label).isEqualTo("İsteğe bağlı veri toplamaya izin ver")
        assertThat(repo.find(1, "en")?.language).isEqualTo("en")
        assertThat(repo.find(1, "de")).isNull()
        assertThat(repo.find(999, "en")).isNull()
    }

    @Test
    fun `hasReconsentAfter is true only for a later flagged version`() = withTestDatabase { db -> // AC-23
        val repo = ExposedConsentTextRepository(db)
        assertThat(repo.hasReconsentAfter(1)).isFalse()

        PostgresTestDb.insertTextVersion(9001, requiresReconsent = true)
        PostgresTestDb.insertTextVersion(9002, requiresReconsent = false)

        assertThat(repo.hasReconsentAfter(1)).isTrue()
        assertThat(repo.hasReconsentAfter(9000)).isTrue()
        assertThat(repo.hasReconsentAfter(9001)).isFalse() // the flagged version itself does not count
        assertThat(repo.hasReconsentAfter(9002)).isFalse()
    }
}

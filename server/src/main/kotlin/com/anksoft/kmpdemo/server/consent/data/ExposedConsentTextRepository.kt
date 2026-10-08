package com.anksoft.kmpdemo.server.consent.data

import com.anksoft.kmpdemo.server.consent.domain.ConsentTexts
import com.anksoft.kmpdemo.server.consent.repository.ConsentTextRepository
import com.anksoft.kmpdemo.server.db.io
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

class ExposedConsentTextRepository(private val db: Database) : ConsentTextRepository {

    override suspend fun latestVersion(): Int? = db.io {
        val max = ConsentTextVersionsTable.version.max()
        ConsentTextVersionsTable.select(max).single()[max]
    }

    override suspend fun find(version: Int, language: String): ConsentTexts? = db.io {
        ConsentTextsTable.selectAll()
            .where { (ConsentTextsTable.version eq version) and (ConsentTextsTable.language eq language) }
            .singleOrNull()
            ?.let {
                ConsentTexts(
                    version = it[ConsentTextsTable.version],
                    language = it[ConsentTextsTable.language],
                    label = it[ConsentTextsTable.label],
                    description = it[ConsentTextsTable.description],
                    policyUrl = it[ConsentTextsTable.policyUrl],
                )
            }
    }

    override suspend fun hasReconsentAfter(version: Int): Boolean = db.io {
        !ConsentTextVersionsTable.selectAll()
            .where { (ConsentTextVersionsTable.version greater version) and ConsentTextVersionsTable.requiresReconsent }
            .limit(1)
            .empty()
    }
}

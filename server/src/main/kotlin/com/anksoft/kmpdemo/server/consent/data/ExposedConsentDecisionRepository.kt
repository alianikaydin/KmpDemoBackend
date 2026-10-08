package com.anksoft.kmpdemo.server.consent.data

import com.anksoft.kmpdemo.server.consent.domain.ConsentDecision
import com.anksoft.kmpdemo.server.consent.domain.ConsentDecisionStatus
import com.anksoft.kmpdemo.server.consent.domain.ConsentSource
import com.anksoft.kmpdemo.server.consent.domain.NewConsentDecision
import com.anksoft.kmpdemo.server.consent.repository.ConsentDecisionRepository
import com.anksoft.kmpdemo.server.db.io
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.postgresql.util.PSQLException
import java.util.UUID

class ExposedConsentDecisionRepository(private val db: Database) : ConsentDecisionRepository {

    override suspend fun latest(userId: UUID, purpose: String): ConsentDecision? = db.io {
        ConsentDecisionsTable.selectAll()
            .where { (ConsentDecisionsTable.userId eq userId) and (ConsentDecisionsTable.purpose eq purpose) }
            .orderBy(ConsentDecisionsTable.id, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.let {
                ConsentDecision(
                    id = it[ConsentDecisionsTable.id],
                    purpose = it[ConsentDecisionsTable.purpose],
                    status = requireNotNull(ConsentDecisionStatus.fromWire(it[ConsentDecisionsTable.status])),
                    textVersion = it[ConsentDecisionsTable.textVersion],
                    textLanguage = it[ConsentDecisionsTable.textLanguage],
                    decidedAt = it[ConsentDecisionsTable.decidedAt].toInstant(),
                    source = requireNotNull(ConsentSource.fromWire(it[ConsentDecisionsTable.origin])),
                )
            }
    }

    /** Only a foreign-key violation on the user returns false; a missing text still throws. */
    override suspend fun append(userId: UUID, decision: NewConsentDecision): Boolean = try {
        db.io { insertConsentDecision(userId, decision) }
        true
    } catch (e: ExposedSQLException) {
        if (e.sqlState == FOREIGN_KEY_VIOLATION && e.violatesUserForeignKey()) false else throw e
    }

    private fun ExposedSQLException.violatesUserForeignKey(): Boolean =
        (cause as? PSQLException)?.serverErrorMessage?.constraint == USER_FOREIGN_KEY

    private companion object {
        const val FOREIGN_KEY_VIOLATION = "23503"

        /** Name Postgres gives the inline `REFERENCES users` constraint of `consent_decisions.user_id`. */
        const val USER_FOREIGN_KEY = "consent_decisions_user_id_fkey"
    }
}

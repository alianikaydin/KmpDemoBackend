package com.anksoft.kmpdemo.server.consent.data

import com.anksoft.kmpdemo.server.consent.domain.NewConsentDecision
import org.jetbrains.exposed.v1.jdbc.insert
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Inserts one decision row. Must be called inside a transaction: the caller owns the
 * transaction so the insert can share it with other writes (register, ADR-14).
 */
internal fun insertConsentDecision(userId: UUID, decision: NewConsentDecision) {
    ConsentDecisionsTable.insert {
        it[ConsentDecisionsTable.userId] = userId
        it[purpose] = decision.purpose
        it[status] = decision.status.wire
        it[textVersion] = decision.textVersion
        it[textLanguage] = decision.textLanguage
        it[decidedAt] = OffsetDateTime.ofInstant(decision.decidedAt, ZoneOffset.UTC)
        it[origin] = decision.source.wire
    }
}

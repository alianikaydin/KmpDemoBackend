package com.anksoft.kmpdemo.server.consent.repository

import com.anksoft.kmpdemo.server.consent.domain.ConsentDecision
import com.anksoft.kmpdemo.server.consent.domain.NewConsentDecision
import java.util.UUID

/** Append-only history of decisions; the current decision is the newest row of a user and purpose. */
interface ConsentDecisionRepository {
    suspend fun latest(userId: UUID, purpose: String): ConsentDecision?

    /** Appends [decision]; returns false when the user does not exist. */
    suspend fun append(userId: UUID, decision: NewConsentDecision): Boolean
}

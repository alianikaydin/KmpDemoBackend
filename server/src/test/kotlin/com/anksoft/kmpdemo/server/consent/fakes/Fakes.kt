package com.anksoft.kmpdemo.server.consent.fakes

import com.anksoft.kmpdemo.server.consent.domain.ConsentDecision
import com.anksoft.kmpdemo.server.consent.domain.ConsentTexts
import com.anksoft.kmpdemo.server.consent.domain.NewConsentDecision
import com.anksoft.kmpdemo.server.consent.repository.ConsentDecisionRepository
import com.anksoft.kmpdemo.server.consent.repository.ConsentTextRepository
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class FakeConsentTextRepository : ConsentTextRepository {
    private val byKey = LinkedHashMap<Pair<Int, String>, ConsentTexts>()
    private val reconsent = HashSet<Int>()

    /** Adds a version with a text per language; [policyUrl] lets tests use the placeholder host. */
    fun publish(
        version: Int,
        requiresReconsent: Boolean = false,
        languages: List<String> = listOf("en", "tr"),
        policyUrl: String = "https://example.org/privacy",
    ) {
        languages.forEach { byKey[version to it] = ConsentTexts(version, it, "label $it", "description $it", policyUrl) }
        if (requiresReconsent) reconsent += version
    }

    override suspend fun latestVersion(): Int? = byKey.keys.maxOfOrNull { it.first }

    override suspend fun find(version: Int, language: String): ConsentTexts? = byKey[version to language]

    override suspend fun hasReconsentAfter(version: Int): Boolean = reconsent.any { it > version }
}

class FakeConsentDecisionRepository : ConsentDecisionRepository {
    /** Users that exist; null means every user exists. */
    var knownUsers: MutableSet<UUID>? = null

    val rows = CopyOnWriteArrayList<Pair<UUID, ConsentDecision>>()

    override suspend fun latest(userId: UUID, purpose: String): ConsentDecision? =
        rows.lastOrNull { it.first == userId && it.second.purpose == purpose }?.second

    override suspend fun append(userId: UUID, decision: NewConsentDecision): Boolean {
        if (knownUsers?.contains(userId) == false) return false
        add(userId, decision)
        return true
    }

    /** Unconditional insert, also used by the fake user repository for the register path. */
    fun add(userId: UUID, decision: NewConsentDecision) {
        rows += userId to ConsentDecision(
            id = rows.size + 1L,
            purpose = decision.purpose,
            status = decision.status,
            textVersion = decision.textVersion,
            textLanguage = decision.textLanguage,
            decidedAt = decision.decidedAt,
            source = decision.source,
        )
    }
}

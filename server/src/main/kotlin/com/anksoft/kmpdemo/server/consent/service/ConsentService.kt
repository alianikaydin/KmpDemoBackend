package com.anksoft.kmpdemo.server.consent.service

import com.anksoft.kmpdemo.contract.consent.ConsentLanguages
import com.anksoft.kmpdemo.server.consent.domain.AccountConsent
import com.anksoft.kmpdemo.server.consent.domain.ConsentDecisionInput
import com.anksoft.kmpdemo.server.consent.domain.ConsentDecisionStatus
import com.anksoft.kmpdemo.server.consent.domain.ConsentError
import com.anksoft.kmpdemo.server.consent.domain.ConsentResult
import com.anksoft.kmpdemo.server.consent.domain.ConsentSource
import com.anksoft.kmpdemo.server.consent.domain.ConsentTexts
import com.anksoft.kmpdemo.server.consent.domain.NewConsentDecision
import com.anksoft.kmpdemo.server.consent.domain.PURPOSE_OPTIONAL_DATA
import com.anksoft.kmpdemo.server.consent.domain.toAccountConsent
import com.anksoft.kmpdemo.server.consent.repository.ConsentDecisionRepository
import com.anksoft.kmpdemo.server.consent.repository.ConsentTextRepository
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Consent business rules. Knows nothing about HTTP or the database; routes
 * translate the [ConsentResult]s into status codes.
 */
class ConsentService(
    private val texts: ConsentTextRepository,
    private val decisions: ConsentDecisionRepository,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(ConsentService::class.java)

    /**
     * The current text version in the requested language. A missing, malformed or
     * unsupported language falls back to English.
     */
    suspend fun currentTexts(rawLanguage: String?): ConsentTexts {
        val version = texts.latestVersion() ?: error("No consent text version is published")
        val requested = normalizeLanguage(rawLanguage)
        return (requested?.let { texts.find(version, it) } ?: texts.find(version, ConsentLanguages.FALLBACK))
            ?: error("Consent text version $version has no ${ConsentLanguages.FALLBACK} text")
    }

    suspend fun accountConsent(userId: UUID): AccountConsent {
        val latest = decisions.latest(userId, PURPOSE_OPTIONAL_DATA) ?: return AccountConsent.NONE
        return latest.toAccountConsent(reconsentRequired(latest.status, latest.textVersion))
    }

    /**
     * Validates a wire decision without storing anything. The text pair must exist
     * (it does not have to be the latest); the time is the server's.
     */
    suspend fun prepareDecision(input: ConsentDecisionInput, source: ConsentSource): ConsentResult<NewConsentDecision> {
        val status = ConsentDecisionStatus.fromWire(input.status)
            ?: return ConsentResult.Err(ConsentError.INVALID_INPUT)
        val language = normalizeLanguage(input.textLanguage)
        if (language == null || texts.find(input.textVersion, language) == null) {
            return ConsentResult.Err(ConsentError.UNKNOWN_TEXT_VERSION)
        }
        return ConsentResult.Ok(
            NewConsentDecision(
                purpose = PURPOSE_OPTIONAL_DATA,
                status = status,
                textVersion = input.textVersion,
                textLanguage = language,
                decidedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS),
                source = source,
            ),
        )
    }

    /** Appends the decision unless it repeats the latest one, then returns the resulting consent. */
    suspend fun updateConsent(userId: UUID, input: ConsentDecisionInput): ConsentResult<AccountConsent> {
        val prepared = when (val result = prepareDecision(input, ConsentSource.UPDATE)) {
            is ConsentResult.Err -> return result
            is ConsentResult.Ok -> result.value
        }
        val latest = decisions.latest(userId, PURPOSE_OPTIONAL_DATA)
        val repeated = latest != null &&
            latest.status == prepared.status &&
            latest.textVersion == prepared.textVersion &&
            latest.textLanguage == prepared.textLanguage
        if (repeated) return ConsentResult.Ok(accountConsent(userId))
        if (!decisions.append(userId, prepared)) return ConsentResult.Err(ConsentError.ACCOUNT_NOT_FOUND)
        return ConsentResult.Ok(describe(prepared))
    }

    /** The consent a freshly prepared decision results in (used right after it is stored). */
    suspend fun describe(decision: NewConsentDecision): AccountConsent =
        decision.toAccountConsent(reconsentRequired(decision.status, decision.textVersion))

    /** Logs a warning when the latest text still points to a placeholder policy URL. */
    suspend fun warnIfPlaceholderTexts() {
        val version = texts.latestVersion() ?: return
        val placeholder = texts.find(version, ConsentLanguages.FALLBACK)?.policyUrl?.contains("example.com") == true
        // Only the version number is logged, never the URL itself.
        if (placeholder) log.warn("Consent text version {} still uses a placeholder policy URL", version)
    }

    private suspend fun reconsentRequired(status: ConsentDecisionStatus, version: Int): Boolean =
        status == ConsentDecisionStatus.GRANTED && texts.hasReconsentAfter(version)

    /** `tr-TR` and `TR_tr` become `tr`; anything that is not 2-8 letters becomes null. */
    internal fun normalizeLanguage(raw: String?): String? {
        val base = raw?.trim()?.lowercase()?.split('-', '_')?.firstOrNull().orEmpty()
        return base.takeIf { LANGUAGE_PATTERN.matches(it) }
    }

    private companion object {
        val LANGUAGE_PATTERN = Regex("^[a-z]{2,8}$")
    }
}

package com.anksoft.kmpdemo.server.consent.domain

import java.time.Instant

/** The only consent purpose in this version; it is stored per decision but not exposed on the wire. */
const val PURPOSE_OPTIONAL_DATA = "optional_data"

/** Stored decision statuses. The wire format uses plain strings (ADR-13); [wire] is that string. */
enum class ConsentDecisionStatus(val wire: String) {
    GRANTED("granted"),
    DENIED("denied");

    companion object {
        fun fromWire(value: String): ConsentDecisionStatus? = entries.firstOrNull { it.wire == value }
    }
}

enum class ConsentSource(val wire: String) {
    REGISTER("register"),
    UPDATE("update");

    companion object {
        fun fromWire(value: String): ConsentSource? = entries.firstOrNull { it.wire == value }
    }
}

/** One consent text in one language. */
data class ConsentTexts(
    val version: Int,
    val language: String,
    val label: String,
    val description: String,
    val policyUrl: String,
)

/** A decision exactly as it came from the wire; nothing is validated yet. */
data class ConsentDecisionInput(val status: String, val textVersion: Int, val textLanguage: String)

/** A validated decision that is ready to be appended; [decidedAt] is the server time. */
data class NewConsentDecision(
    val purpose: String,
    val status: ConsentDecisionStatus,
    val textVersion: Int,
    val textLanguage: String,
    val decidedAt: Instant,
    val source: ConsentSource,
)

/** A stored decision. */
data class ConsentDecision(
    val id: Long,
    val purpose: String,
    val status: ConsentDecisionStatus,
    val textVersion: Int,
    val textLanguage: String,
    val decidedAt: Instant,
    val source: ConsentSource,
)

/** The account's current consent; a null [status] means no decision was ever made. */
data class AccountConsent(
    val status: ConsentDecisionStatus?,
    val textVersion: Int?,
    val textLanguage: String?,
    val decidedAt: Instant?,
    val reconsentRequired: Boolean,
) {
    companion object {
        val NONE = AccountConsent(null, null, null, null, reconsentRequired = false)
    }
}

fun NewConsentDecision.toAccountConsent(reconsentRequired: Boolean = false) =
    AccountConsent(status, textVersion, textLanguage, decidedAt, reconsentRequired)

fun ConsentDecision.toAccountConsent(reconsentRequired: Boolean) =
    AccountConsent(status, textVersion, textLanguage, decidedAt, reconsentRequired)

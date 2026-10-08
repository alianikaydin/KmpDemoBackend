package com.anksoft.kmpdemo.contract.consent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Current consent text in one language, returned by `GET consent/texts`. */
@Serializable
public data class ConsentTextsDto(
    val version: Int,
    val language: String,
    val label: String,
    val description: String,
    @SerialName("policy_url") val policyUrl: String,
)

/**
 * Body of `PUT account/consent` and the optional `consent` field of the
 * register request. [status] must be [ConsentStatus.GRANTED] or
 * [ConsentStatus.DENIED]; [textVersion] and [textLanguage] identify the text
 * the user saw.
 */
@Serializable
public data class ConsentDecisionDto(
    val status: String,
    @SerialName("text_version") val textVersion: Int,
    @SerialName("text_language") val textLanguage: String,
)

/**
 * The account's current consent. [status] is one of [ConsentStatus]; clients
 * treat an unknown value as [ConsentStatus.NONE]. [decidedAt] is ISO-8601 UTC.
 */
@Serializable
public data class AccountConsentDto(
    val status: String,
    @SerialName("text_version") val textVersion: Int? = null,
    @SerialName("text_language") val textLanguage: String? = null,
    @SerialName("decided_at") val decidedAt: String? = null,
    @SerialName("reconsent_required") val reconsentRequired: Boolean,
)

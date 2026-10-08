package com.anksoft.kmpdemo.server.consent.routes

import com.anksoft.kmpdemo.contract.consent.AccountConsentDto
import com.anksoft.kmpdemo.contract.consent.ConsentDecisionDto
import com.anksoft.kmpdemo.contract.consent.ConsentStatus
import com.anksoft.kmpdemo.contract.consent.ConsentTextsDto
import com.anksoft.kmpdemo.server.consent.domain.AccountConsent
import com.anksoft.kmpdemo.server.consent.domain.ConsentDecisionInput
import com.anksoft.kmpdemo.server.consent.domain.ConsentTexts

fun ConsentTexts.toDto() = ConsentTextsDto(
    version = version,
    language = language,
    label = label,
    description = description,
    policyUrl = policyUrl,
)

/** A missing decision is reported as `none`; [AccountConsent.decidedAt] becomes an ISO-8601 UTC string. */
fun AccountConsent.toDto() = AccountConsentDto(
    status = status?.wire ?: ConsentStatus.NONE,
    textVersion = textVersion,
    textLanguage = textLanguage,
    decidedAt = decidedAt?.toString(),
    reconsentRequired = reconsentRequired,
)

fun ConsentDecisionDto.toInput() = ConsentDecisionInput(status, textVersion, textLanguage)

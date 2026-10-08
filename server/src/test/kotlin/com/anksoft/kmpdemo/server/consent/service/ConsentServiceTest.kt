package com.anksoft.kmpdemo.server.consent.service

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.anksoft.kmpdemo.server.consent.domain.AccountConsent
import com.anksoft.kmpdemo.server.consent.domain.ConsentDecisionInput
import com.anksoft.kmpdemo.server.consent.domain.ConsentDecisionStatus
import com.anksoft.kmpdemo.server.consent.domain.ConsentError
import com.anksoft.kmpdemo.server.consent.domain.ConsentResult
import com.anksoft.kmpdemo.server.consent.domain.ConsentSource
import com.anksoft.kmpdemo.server.consent.fakes.FakeConsentDecisionRepository
import com.anksoft.kmpdemo.server.consent.fakes.FakeConsentTextRepository
import com.anksoft.kmpdemo.server.support.LogCapture
import com.anksoft.kmpdemo.server.support.MutableClock
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test

class ConsentServiceTest {

    private val texts = FakeConsentTextRepository().apply { publish(1) }
    private val decisions = FakeConsentDecisionRepository()
    private val clock = MutableClock(Instant.parse("2026-10-08T12:00:00.123456Z"))
    private val service = ConsentService(texts, decisions, clock)
    private val userId = UUID.randomUUID()

    private fun input(status: String = "granted", version: Int = 1, language: String = "tr") =
        ConsentDecisionInput(status, version, language)

    private fun <T> ConsentResult<T>.ok(): T = (this as? ConsentResult.Ok)?.value ?: error("expected Ok but was $this")

    private fun ConsentResult<*>.errorOrNull(): ConsentError? = (this as? ConsentResult.Err)?.error

    @Test
    fun `language normalization maps variants to a base language or null`() { // AC-24
        val cases = mapOf(
            "tr" to "tr", "TR" to "tr", " tr-TR " to "tr", "tr_TR" to "tr", "en-US" to "en",
            "xx-!!" to "xx", "" to null, "   " to null, "!!" to null, "toolonglanguage" to null, "1a" to null,
        )
        cases.forEach { (raw, expected) -> assertThat(service.normalizeLanguage(raw), raw).isEqualTo(expected) }
        assertThat(service.normalizeLanguage(null)).isNull()
    }

    @Test
    fun `currentTexts returns the requested language of the latest version`() = runTest { // AC-21
        texts.publish(2)

        val result = service.currentTexts("tr-TR")

        assertThat(result.version).isEqualTo(2)
        assertThat(result.language).isEqualTo("tr")
    }

    @Test
    fun `currentTexts falls back to english for missing and unsupported languages`() = runTest { // AC-24
        listOf(null, "", "de", "xx-!!").forEach { raw ->
            assertThat(service.currentTexts(raw).language, "lang=$raw").isEqualTo("en")
        }
    }

    @Test
    fun `currentTexts falls back to english when the latest version lacks the language`() = runTest { // AC-24
        texts.publish(2, languages = listOf("en"))

        val result = service.currentTexts("tr")

        assertThat(result.version).isEqualTo(2)
        assertThat(result.language).isEqualTo("en")
    }

    @Test
    fun `currentTexts fails loudly when no english text exists`() = runTest { // AC-24
        val broken = ConsentService(FakeConsentTextRepository().apply { publish(1, languages = listOf("tr")) }, decisions, clock)

        val failure = runCatching { broken.currentTexts("de") }.exceptionOrNull()

        assertThat(failure is IllegalStateException).isTrue()
    }

    @Test
    fun `accountConsent is none before any decision`() = runTest { // AC-6
        assertThat(service.accountConsent(userId)).isEqualTo(AccountConsent.NONE)
    }

    @Test
    fun `prepareDecision accepts granted and denied with server time truncated to millis`() = runTest { // AC-22
        val granted = service.prepareDecision(input("granted"), ConsentSource.REGISTER).ok()
        val denied = service.prepareDecision(input("denied", language = "en"), ConsentSource.UPDATE).ok()

        assertThat(granted.status).isEqualTo(ConsentDecisionStatus.GRANTED)
        assertThat(granted.source).isEqualTo(ConsentSource.REGISTER)
        assertThat(granted.decidedAt).isEqualTo(Instant.parse("2026-10-08T12:00:00.123Z"))
        assertThat(denied.status).isEqualTo(ConsentDecisionStatus.DENIED)
        assertThat(denied.textLanguage).isEqualTo("en")
    }

    @Test
    fun `prepareDecision rejects statuses other than granted and denied`() = runTest { // AC-9
        listOf("none", "maybe", "", "GRANTED").forEach { status ->
            assertThat(service.prepareDecision(input(status), ConsentSource.UPDATE).errorOrNull(), status)
                .isEqualTo(ConsentError.INVALID_INPUT)
        }
    }

    @Test
    fun `prepareDecision rejects unknown versions and languages`() = runTest { // AC-5
        assertThat(service.prepareDecision(input(version = 999), ConsentSource.UPDATE).errorOrNull())
            .isEqualTo(ConsentError.UNKNOWN_TEXT_VERSION)
        assertThat(service.prepareDecision(input(language = "de"), ConsentSource.UPDATE).errorOrNull())
            .isEqualTo(ConsentError.UNKNOWN_TEXT_VERSION)
        assertThat(service.prepareDecision(input(language = "!!"), ConsentSource.UPDATE).errorOrNull())
            .isEqualTo(ConsentError.UNKNOWN_TEXT_VERSION)
    }

    @Test
    fun `prepareDecision accepts an older version that still exists`() = runTest { // AC-22
        texts.publish(2)

        assertThat(service.prepareDecision(input(version = 1), ConsentSource.UPDATE).ok().textVersion).isEqualTo(1)
    }

    @Test
    fun `prepareDecision normalizes the decision language`() = runTest { // AC-24
        assertThat(service.prepareDecision(input(language = "TR-tr"), ConsentSource.UPDATE).ok().textLanguage)
            .isEqualTo("tr")
    }

    @Test
    fun `updateConsent stores a decision and returns it with the server time`() = runTest { // AC-8, AC-22
        val result = service.updateConsent(userId, input("granted")).ok()

        assertThat(result.status).isEqualTo(ConsentDecisionStatus.GRANTED)
        assertThat(result.decidedAt).isEqualTo(Instant.parse("2026-10-08T12:00:00.123Z"))
        assertThat(decisions.rows).hasSize(1)
        assertThat(service.accountConsent(userId)).isEqualTo(result)
    }

    @Test
    fun `updateConsent appends history when the decision changes`() = runTest { // AC-9, AC-15
        service.updateConsent(userId, input("granted")).ok()
        clock.advance(Duration.ofMinutes(5))

        val result = service.updateConsent(userId, input("denied")).ok()

        assertThat(result.status).isEqualTo(ConsentDecisionStatus.DENIED)
        assertThat(decisions.rows).hasSize(2)
    }

    @Test
    fun `updateConsent repeating the latest decision adds no row and keeps its time`() = runTest { // AC-8
        val first = service.updateConsent(userId, input("granted")).ok()
        clock.advance(Duration.ofMinutes(5))

        val second = service.updateConsent(userId, input("granted")).ok()

        assertThat(decisions.rows).hasSize(1)
        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `updateConsent with the same status but a new text version appends a row`() = runTest { // AC-22
        service.updateConsent(userId, input("granted")).ok()
        texts.publish(2)

        service.updateConsent(userId, input("granted", version = 2)).ok()

        assertThat(decisions.rows).hasSize(2)
    }

    @Test
    fun `updateConsent propagates validation errors without storing`() = runTest { // AC-9
        assertThat(service.updateConsent(userId, input("none")).errorOrNull()).isEqualTo(ConsentError.INVALID_INPUT)
        assertThat(service.updateConsent(userId, input(version = 999)).errorOrNull())
            .isEqualTo(ConsentError.UNKNOWN_TEXT_VERSION)
        assertThat(decisions.rows).hasSize(0)
    }

    @Test
    fun `updateConsent reports a missing account`() = runTest { // AC-28
        decisions.knownUsers = mutableSetOf()

        assertThat(service.updateConsent(userId, input()).errorOrNull()).isEqualTo(ConsentError.ACCOUNT_NOT_FOUND)
    }

    @Test
    fun `reconsent is required only for a grant followed by a flagged version`() = runTest { // AC-23
        service.updateConsent(userId, input("granted")).ok()
        assertThat(service.accountConsent(userId).reconsentRequired).isFalse()

        texts.publish(2, requiresReconsent = false)
        assertThat(service.accountConsent(userId).reconsentRequired).isFalse()

        texts.publish(3, requiresReconsent = true)
        val consent = service.accountConsent(userId)
        assertThat(consent.reconsentRequired).isTrue()
        assertThat(consent.status).isEqualTo(ConsentDecisionStatus.GRANTED) // the decision itself is kept
    }

    @Test
    fun `reconsent is never required for a denied decision`() = runTest { // AC-23
        service.updateConsent(userId, input("denied")).ok()
        texts.publish(2, requiresReconsent = true)

        assertThat(service.accountConsent(userId).reconsentRequired).isFalse()
    }

    @Test
    fun `reconsent is cleared by a decision on the flagged version`() = runTest { // AC-23
        service.updateConsent(userId, input("granted")).ok()
        texts.publish(2, requiresReconsent = true)

        val result = service.updateConsent(userId, input("granted", version = 2)).ok()

        assertThat(result.reconsentRequired).isFalse()
    }

    @Test
    fun `describe flags a fresh decision on an outdated version`() = runTest { // AC-23
        texts.publish(2, requiresReconsent = true)
        val prepared = service.prepareDecision(input("granted", version = 1), ConsentSource.REGISTER).ok()

        assertThat(service.describe(prepared).reconsentRequired).isTrue()
    }

    @Test
    fun `warnIfPlaceholderTexts logs the version but not the url`() = runTest { // AC-21
        val placeholder = FakeConsentTextRepository().apply { publish(7, policyUrl = "https://example.com/privacy") }

        LogCapture().use { logs ->
            ConsentService(placeholder, decisions, clock).warnIfPlaceholderTexts()

            assertThat(logs.text()).contains("placeholder policy URL")
            assertThat(logs.text()).contains("7")
            assertThat(logs.text()).doesNotContain("https://example.com")
        }
    }

    @Test
    fun `warnIfPlaceholderTexts stays quiet for real urls`() = runTest { // AC-21
        LogCapture().use { logs ->
            service.warnIfPlaceholderTexts()

            assertThat(logs.text()).doesNotContain("placeholder")
        }
    }
}

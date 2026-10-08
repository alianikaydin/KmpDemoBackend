package com.anksoft.kmpdemo.contract.consent

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.anksoft.kmpdemo.contract.error.ErrorCodes
import kotlin.test.Test

class ConsentValuesTest {

    @Test
    fun `status constants are pinned`() {
        assertThat(ConsentStatus.NONE).isEqualTo("none")
        assertThat(ConsentStatus.GRANTED).isEqualTo("granted")
        assertThat(ConsentStatus.DENIED).isEqualTo("denied")
    }

    @Test
    fun `fallback language is english`() {
        assertThat(ConsentLanguages.FALLBACK).isEqualTo("en")
    }

    @Test
    fun `path constants are pinned`() {
        assertThat(ConsentPaths.TEXTS).isEqualTo("consent/texts")
        assertThat(ConsentPaths.ACCOUNT_CONSENT).isEqualTo("account/consent")
        assertThat(ConsentPaths.LANG_PARAM).isEqualTo("lang")
    }

    @Test
    fun `unknown consent version error code is pinned`() {
        assertThat(ErrorCodes.UNKNOWN_CONSENT_VERSION).isEqualTo("unknown_consent_version")
    }
}

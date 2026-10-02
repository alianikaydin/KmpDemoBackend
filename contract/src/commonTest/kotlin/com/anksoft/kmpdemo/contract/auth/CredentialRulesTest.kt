package com.anksoft.kmpdemo.contract.auth

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

class CredentialRulesTest {

    @Test
    fun `accepts well formed emails`() { // AC-1
        listOf(
            "user@example.com",
            "first.last@example.co.uk",
            "user+tag@example.io",
            "u@sub.domain.example.com",
        ).forEach { assertThat(CredentialRules.isValidEmail(it), name = it).isTrue() }
    }

    @Test
    fun `rejects malformed emails`() { // AC-3
        listOf(
            "", "   ", "plainstring", "no-at-sign.com", "@example.com", "user@",
            "user@example", "user@.com", "user name@example.com", "user@exam ple.com",
            "two@@example.com",
        ).forEach { assertThat(CredentialRules.isValidEmail(it), name = it).isFalse() }
    }

    @Test
    fun `trims surrounding whitespace before validating email`() {
        assertThat(CredentialRules.isValidEmail("  user@example.com  ")).isTrue()
    }

    @Test
    fun `normalizes email by trimming and lower casing`() { // AC-2
        assertThat(CredentialRules.normalizeEmail(" A@B.COM ")).isEqualTo("a@b.com")
    }

    @Test
    fun `password meeting every rule has no violations`() {
        assertThat(CredentialRules.passwordViolations("Password1")).isEmpty()
    }

    @Test
    fun `password reports every unmet rule at once`() { // AC-3
        assertThat(CredentialRules.passwordViolations("abc")).containsExactlyInAnyOrder(
            PasswordRule.TOO_SHORT,
            PasswordRule.NO_DIGIT,
            PasswordRule.NO_UPPERCASE,
        )
    }

    @Test
    fun `password of exactly minimum length is accepted`() {
        assertThat(CredentialRules.passwordViolations("Passwo1d")).isEmpty()
    }

    @Test
    fun `password missing only a digit reports only that`() {
        assertThat(CredentialRules.passwordViolations("PasswordX"))
            .containsExactlyInAnyOrder(PasswordRule.NO_DIGIT)
    }

    @Test
    fun `password missing only uppercase reports only that`() {
        assertThat(CredentialRules.passwordViolations("password1"))
            .containsExactlyInAnyOrder(PasswordRule.NO_UPPERCASE)
    }

    @Test
    fun `unicode letters and digits satisfy the rules`() {
        assertThat(CredentialRules.passwordViolations("Şifre123")).isEmpty()
    }

    @Test
    fun `password longer than the maximum is rejected`() { // AC-3
        val tooLong = "A1" + "a".repeat(CredentialRules.MAX_PASSWORD_LENGTH)
        assertThat(CredentialRules.passwordViolations(tooLong))
            .containsExactlyInAnyOrder(PasswordRule.TOO_LONG)
        val atLimit = "A1" + "a".repeat(CredentialRules.MAX_PASSWORD_LENGTH - 2)
        assertThat(CredentialRules.passwordViolations(atLimit)).isEmpty()
    }
}

package com.anksoft.kmpdemo.contract.auth

/** A password rule that was not met. */
public enum class PasswordRule { TOO_SHORT, TOO_LONG, NO_DIGIT, NO_UPPERCASE }

/**
 * Credential rules shared by the server and its clients so both sides always
 * agree. Pure Kotlin with no platform dependencies.
 */
public object CredentialRules {
    public const val MIN_PASSWORD_LENGTH: Int = 8
    public const val MAX_PASSWORD_LENGTH: Int = 128
    public const val MAX_EMAIL_LENGTH: Int = 254

    // Pragmatic rather than RFC-complete: one @, no whitespace, a dotted TLD of >= 2 chars.
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9!#\$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#\$%&'*+/=?^_`{|}~-]+)*@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*\\.[A-Za-z]{2,}\$")

    /** Canonical stored form of an e-mail: trimmed and lower-cased (locale independent). */
    public fun normalizeEmail(raw: String): String = raw.trim().lowercase()

    public fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email.trim())

    /** Returns every unmet rule so a UI can render a live checklist. */
    public fun passwordViolations(password: String): Set<PasswordRule> = buildSet {
        if (password.length < MIN_PASSWORD_LENGTH) add(PasswordRule.TOO_SHORT)
        if (password.length > MAX_PASSWORD_LENGTH) add(PasswordRule.TOO_LONG)
        if (password.none { it.isDigit() }) add(PasswordRule.NO_DIGIT)
        if (password.none { it.isUpperCase() }) add(PasswordRule.NO_UPPERCASE)
    }
}

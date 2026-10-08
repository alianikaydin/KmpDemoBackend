package com.anksoft.kmpdemo.contract.consent

/**
 * Values of the `status` field. Plain strings instead of an enum on purpose
 * (ADR-13): a new value must never break an older client. Clients treat any
 * value they do not know as [NONE].
 */
public object ConsentStatus {
    public const val NONE: String = "none"
    public const val GRANTED: String = "granted"
    public const val DENIED: String = "denied"
}

public object ConsentLanguages {
    /** Language used when the requested one is missing or has no text. */
    public const val FALLBACK: String = "en"
}

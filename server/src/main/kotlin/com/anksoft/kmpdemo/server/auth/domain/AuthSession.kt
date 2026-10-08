package com.anksoft.kmpdemo.server.auth.domain

import com.anksoft.kmpdemo.server.consent.domain.AccountConsent

/** [consent] is set by register only; login and refresh leave it null. */
data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val user: User,
    val consent: AccountConsent? = null,
)

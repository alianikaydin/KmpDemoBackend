package com.anksoft.kmpdemo.contract.auth

import com.anksoft.kmpdemo.contract.consent.AccountConsentDto
import com.anksoft.kmpdemo.contract.consent.ConsentDecisionDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format of the auth API, published as a contract shared between the
 * server and its clients. Changes inside `/api/v1` must be additive only
 * (see ADR-9); the golden strings in `WireFormatTest` guard the JSON shape.
 */

@Serializable
public data class LoginRequestDto(
    val email: String,
    val password: String,
)

@Serializable
public data class RegisterRequestDto(
    val email: String,
    val password: String,
    val consent: ConsentDecisionDto? = null,
)

@Serializable
public data class RefreshTokenRequestDto(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
public data class AuthResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val user: UserDto,
    /** Filled by register only; login and refresh leave it out. */
    val consent: AccountConsentDto? = null,
)

@Serializable
public data class UserDto(
    val id: String,
    val email: String,
    val name: String? = null,
)

package com.anksoft.kmpdemo.server.auth.routes

import com.anksoft.kmpdemo.contract.auth.AuthResponseDto
import com.anksoft.kmpdemo.contract.auth.UserDto
import com.anksoft.kmpdemo.server.auth.domain.AuthSession
import com.anksoft.kmpdemo.server.auth.domain.User
import com.anksoft.kmpdemo.server.consent.routes.toDto

fun User.toDto() = UserDto(id = id.toString(), email = email, name = name)

fun AuthSession.toDto() = AuthResponseDto(
    accessToken = accessToken,
    refreshToken = refreshToken,
    user = user.toDto(),
    consent = consent?.toDto(),
)

package com.anksoft.kmpdemo.server.auth.domain

data class AuthSession(val accessToken: String, val refreshToken: String, val user: User)

package com.anksoft.kmpdemo.contract.auth

/** Relative route paths of the auth API. Clients put [API_PREFIX] into their base URL. */
public object AuthPaths {
    public const val API_PREFIX: String = "api/v1/"
    public const val LOGIN: String = "auth/login"
    public const val REGISTER: String = "auth/register"
    public const val REFRESH: String = "auth/refresh"
    public const val ME: String = "auth/me"
    public const val LOGOUT: String = "auth/logout"
}

package com.anksoft.kmpdemo.server.support

import com.anksoft.kmpdemo.contract.auth.AuthPaths
import com.anksoft.kmpdemo.contract.auth.AuthResponseDto
import com.anksoft.kmpdemo.contract.auth.LoginRequestDto
import com.anksoft.kmpdemo.contract.auth.RefreshTokenRequestDto
import com.anksoft.kmpdemo.contract.auth.RegisterRequestDto
import com.anksoft.kmpdemo.contract.consent.ConsentDecisionDto
import com.anksoft.kmpdemo.contract.consent.ConsentPaths
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

const val VALID_PASSWORD = "Password1"

private fun path(relative: String) = "/" + AuthPaths.API_PREFIX + relative

suspend fun HttpClient.register(
    email: String = "ali@example.com",
    password: String = VALID_PASSWORD,
    consent: ConsentDecisionDto? = null,
): HttpResponse = post(path(AuthPaths.REGISTER)) {
    contentType(ContentType.Application.Json)
    setBody(RegisterRequestDto(email, password, consent))
}

suspend fun HttpClient.login(email: String = "ali@example.com", password: String = VALID_PASSWORD): HttpResponse =
    post(path(AuthPaths.LOGIN)) {
        contentType(ContentType.Application.Json)
        setBody(LoginRequestDto(email, password))
    }

suspend fun HttpClient.refresh(refreshToken: String): HttpResponse = post(path(AuthPaths.REFRESH)) {
    contentType(ContentType.Application.Json)
    setBody(RefreshTokenRequestDto(refreshToken))
}

suspend fun HttpClient.logout(refreshToken: String): HttpResponse = post(path(AuthPaths.LOGOUT)) {
    contentType(ContentType.Application.Json)
    setBody(RefreshTokenRequestDto(refreshToken))
}

suspend fun HttpClient.me(accessToken: String?, rawAuthorization: String? = null): HttpResponse =
    get(path(AuthPaths.ME)) {
        val value = rawAuthorization ?: accessToken?.let { "Bearer $it" }
        if (value != null) header(HttpHeaders.Authorization, value)
    }

suspend fun HttpClient.postRaw(route: String, json: String): HttpResponse = post(path(route)) {
    contentType(ContentType.Application.Json)
    setBody(json)
}

/** Registers a user and returns its session, failing the test if registration does not succeed. */
suspend fun HttpClient.registerOk(email: String = "ali@example.com", password: String = VALID_PASSWORD): AuthResponseDto {
    val response = register(email, password)
    check(response.status.value == 200) { "register failed with ${response.status}" }
    return response.body()
}

suspend fun HttpClient.getTexts(lang: String? = null, authorization: String? = null): HttpResponse =
    get(path(ConsentPaths.TEXTS)) {
        if (lang != null) url.parameters.append(ConsentPaths.LANG_PARAM, lang)
        if (authorization != null) header(HttpHeaders.Authorization, authorization)
    }

suspend fun HttpClient.getAccountConsent(accessToken: String?): HttpResponse = get(path(ConsentPaths.ACCOUNT_CONSENT)) {
    if (accessToken != null) header(HttpHeaders.Authorization, "Bearer $accessToken")
}

suspend fun HttpClient.putAccountConsent(accessToken: String?, decision: ConsentDecisionDto): HttpResponse =
    put(path(ConsentPaths.ACCOUNT_CONSENT)) {
        if (accessToken != null) header(HttpHeaders.Authorization, "Bearer $accessToken")
        contentType(ContentType.Application.Json)
        setBody(decision)
    }

suspend fun HttpClient.putAccountConsentRaw(accessToken: String?, json: String): HttpResponse =
    put(path(ConsentPaths.ACCOUNT_CONSENT)) {
        if (accessToken != null) header(HttpHeaders.Authorization, "Bearer $accessToken")
        contentType(ContentType.Application.Json)
        setBody(json)
    }

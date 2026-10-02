package com.anksoft.kmpdemo.server.auth.security

import com.anksoft.kmpdemo.server.auth.domain.RefreshTokenGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** 256-bit random opaque tokens; only their SHA-256 hex digest is ever stored. */
class SecureRefreshTokenGenerator(private val random: SecureRandom = SecureRandom()) : RefreshTokenGenerator {
    override fun generate(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    override fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

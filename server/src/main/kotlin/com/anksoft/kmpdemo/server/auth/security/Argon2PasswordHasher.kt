package com.anksoft.kmpdemo.server.auth.security

import com.anksoft.kmpdemo.server.auth.domain.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder

/** Argon2id with OWASP parameters (19 MiB, 2 iterations, 1 lane); output is a PHC string. */
class Argon2PasswordHasher : PasswordHasher {
    private val encoder = Argon2PasswordEncoder(
        /* saltLength = */ 16,
        /* hashLength = */ 32,
        /* parallelism = */ 1,
        /* memory = */ 19456,
        /* iterations = */ 2,
    )

    override suspend fun hash(raw: String): String = withContext(Dispatchers.Default) { requireNotNull(encoder.encode(raw)) }

    override suspend fun verify(raw: String, hash: String): Boolean =
        withContext(Dispatchers.Default) { encoder.matches(raw, hash) }
}

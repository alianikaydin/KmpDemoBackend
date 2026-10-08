package com.anksoft.kmpdemo.server.auth.repository

import com.anksoft.kmpdemo.server.auth.domain.NewUser
import com.anksoft.kmpdemo.server.auth.domain.User
import com.anksoft.kmpdemo.server.auth.domain.UserCredentials
import com.anksoft.kmpdemo.server.consent.domain.NewConsentDecision
import java.util.UUID

interface UserRepository {
    suspend fun findByEmail(email: String): UserCredentials?
    suspend fun findById(id: UUID): User?

    /**
     * Returns the created user, or null when the e-mail is already taken. The user and the
     * optional [initialConsent] are written in one transaction: either both exist afterwards or neither.
     */
    suspend fun create(user: NewUser, initialConsent: NewConsentDecision? = null): User?
}

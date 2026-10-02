package com.anksoft.kmpdemo.server.auth.repository

import com.anksoft.kmpdemo.server.auth.domain.NewUser
import com.anksoft.kmpdemo.server.auth.domain.User
import com.anksoft.kmpdemo.server.auth.domain.UserCredentials
import java.util.UUID

interface UserRepository {
    suspend fun findByEmail(email: String): UserCredentials?
    suspend fun findById(id: UUID): User?

    /** Returns the created user, or null when the e-mail is already taken. */
    suspend fun create(user: NewUser): User?
}

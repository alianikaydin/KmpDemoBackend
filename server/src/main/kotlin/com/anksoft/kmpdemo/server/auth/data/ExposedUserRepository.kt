package com.anksoft.kmpdemo.server.auth.data

import com.anksoft.kmpdemo.server.auth.domain.NewUser
import com.anksoft.kmpdemo.server.auth.domain.User
import com.anksoft.kmpdemo.server.auth.domain.UserCredentials
import com.anksoft.kmpdemo.server.auth.repository.UserRepository
import com.anksoft.kmpdemo.server.consent.data.insertConsentDecision
import com.anksoft.kmpdemo.server.consent.domain.NewConsentDecision
import com.anksoft.kmpdemo.server.db.io
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

class ExposedUserRepository(private val db: Database) : UserRepository {

    override suspend fun findByEmail(email: String): UserCredentials? = db.io {
        UsersTable.selectAll().where { UsersTable.email eq email }.singleOrNull()
            ?.let { UserCredentials(it.toUser(), it[UsersTable.passwordHash]) }
    }

    override suspend fun findById(id: UUID): User? = db.io {
        UsersTable.selectAll().where { UsersTable.id eq id }.singleOrNull()?.toUser()
    }

    /** One transaction: a unique violation returns null, any other failure rolls back both inserts and throws. */
    override suspend fun create(user: NewUser, initialConsent: NewConsentDecision?): User? = try {
        db.io {
            UsersTable.insert {
                it[id] = user.id
                it[email] = user.email
                it[passwordHash] = user.passwordHash
            }
            initialConsent?.let { insertConsentDecision(user.id, it) }
        }
        User(user.id, user.email, null)
    } catch (e: ExposedSQLException) {
        if (e.sqlState == UNIQUE_VIOLATION) null else throw e
    }

    private fun ResultRow.toUser() = User(this[UsersTable.id], this[UsersTable.email], this[UsersTable.name])

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
    }
}

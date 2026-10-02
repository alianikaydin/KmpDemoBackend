package com.anksoft.kmpdemo.server.auth.domain

import java.util.UUID

data class User(val id: UUID, val email: String, val name: String?)

/** A user to be stored; [email] is already normalized and [passwordHash] is a PHC string. */
data class NewUser(val id: UUID, val email: String, val passwordHash: String)

data class UserCredentials(val user: User, val passwordHash: String)

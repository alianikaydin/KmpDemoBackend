package com.anksoft.kmpdemo.server.consent.data

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/** Mirrors `V3__create_consent.sql`; the schema itself is owned by Flyway, not by Exposed. */
object ConsentTextVersionsTable : Table("consent_text_versions") {
    val version = integer("version")
    val requiresReconsent = bool("requires_reconsent")
    val publishedAt = timestampWithTimeZone("published_at")
    override val primaryKey = PrimaryKey(version)
}

object ConsentTextsTable : Table("consent_texts") {
    val version = integer("version")
    val language = varchar("language", 8)
    val label = varchar("label", 200)
    val description = varchar("description", 2000)
    val policyUrl = varchar("policy_url", 2048)
    override val primaryKey = PrimaryKey(version, language)
}

object ConsentDecisionsTable : Table("consent_decisions") {
    val id = long("id").autoIncrement()
    val userId = javaUUID("user_id")
    val purpose = varchar("purpose", 32)
    val status = varchar("status", 16)
    val textVersion = integer("text_version")
    val textLanguage = varchar("text_language", 8)
    val decidedAt = timestampWithTimeZone("decided_at")
    val origin = varchar("source", 16)
    override val primaryKey = PrimaryKey(id)
}

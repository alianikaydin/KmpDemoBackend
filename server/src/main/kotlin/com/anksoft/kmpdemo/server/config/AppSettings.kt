package com.anksoft.kmpdemo.server.config

import java.time.Duration

/**
 * Typed application settings read once from environment variables (12-factor).
 * Failures name the offending variable but never its value.
 */
data class AppSettings(
    val port: Int,
    val databaseUrl: String,
    val databaseUser: String,
    val databasePassword: String,
    val databasePoolSize: Int,
    val dbMigrateOnStart: Boolean,
    val jwtSecret: String,
    val jwtIssuer: String,
    val jwtAudience: String,
    val accessTokenTtl: Duration,
    val refreshTokenTtl: Duration,
    val corsAllowedOrigins: List<String>,
    /** Requests per minute per client IP on auth routes; 0 disables rate limiting. */
    val rateLimitAuthPerMinute: Int,
    val jsonLogs: Boolean,
    val swaggerEnabled: Boolean,
) {
    override fun toString(): String =
        "AppSettings(port=$port, databaseUrl=<masked>, databaseUser=<masked>, databasePassword=<masked>, " +
            "databasePoolSize=$databasePoolSize, dbMigrateOnStart=$dbMigrateOnStart, jwtSecret=<masked>, " +
            "jwtIssuer=$jwtIssuer, jwtAudience=$jwtAudience, accessTokenTtl=$accessTokenTtl, " +
            "refreshTokenTtl=$refreshTokenTtl, corsAllowedOrigins=$corsAllowedOrigins, " +
            "rateLimitAuthPerMinute=$rateLimitAuthPerMinute, jsonLogs=$jsonLogs, swaggerEnabled=$swaggerEnabled)"

    companion object {
        const val MIN_JWT_SECRET_BYTES = 32

        fun fromEnv(env: Map<String, String>): AppSettings {
            fun raw(name: String): String? = env[name]?.trim()?.takeIf { it.isNotEmpty() }
            fun invalid(name: String): Nothing = throw IllegalStateException("Missing or invalid env var: $name")
            fun required(name: String): String = raw(name) ?: invalid(name)
            fun int(name: String, default: Int, min: Int, max: Int = Int.MAX_VALUE): Int {
                val value = raw(name) ?: return default
                return value.toIntOrNull()?.takeIf { it in min..max } ?: invalid(name)
            }
            fun bool(name: String, default: Boolean): Boolean = when (raw(name)?.lowercase()) {
                null -> default
                "true" -> true
                "false" -> false
                else -> invalid(name)
            }
            fun duration(name: String, default: String): Duration {
                val parsed = try {
                    Duration.parse(raw(name) ?: default)
                } catch (_: java.time.format.DateTimeParseException) {
                    invalid(name)
                }
                return parsed.takeIf { !it.isZero && !it.isNegative } ?: invalid(name)
            }

            val secret = required("JWT_SECRET")
            if (secret.toByteArray(Charsets.UTF_8).size < MIN_JWT_SECRET_BYTES) invalid("JWT_SECRET")

            val logFormat = raw("LOG_FORMAT")?.lowercase() ?: "plain"
            if (logFormat != "plain" && logFormat != "json") invalid("LOG_FORMAT")

            return AppSettings(
                port = int("PORT", 8081, 1, 65535),
                databaseUrl = required("DATABASE_URL"),
                databaseUser = required("DATABASE_USER"),
                databasePassword = required("DATABASE_PASSWORD"),
                databasePoolSize = int("DATABASE_POOL_SIZE", 10, 1),
                dbMigrateOnStart = bool("DB_MIGRATE_ON_START", true),
                jwtSecret = secret,
                jwtIssuer = raw("JWT_ISSUER") ?: "kmp-demo-server",
                jwtAudience = raw("JWT_AUDIENCE") ?: "kmp-demo-app",
                accessTokenTtl = duration("ACCESS_TOKEN_TTL", "PT15M"),
                refreshTokenTtl = duration("REFRESH_TOKEN_TTL", "P30D"),
                corsAllowedOrigins = raw("CORS_ALLOWED_ORIGINS")
                    ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?: emptyList(),
                rateLimitAuthPerMinute = int("RATE_LIMIT_AUTH_PER_MINUTE", 20, 0),
                jsonLogs = logFormat == "json",
                swaggerEnabled = bool("SWAGGER_ENABLED", false),
            )
        }
    }
}

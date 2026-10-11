package com.anksoft.kmpdemo.server.appversion.domain

/**
 * A strict `MAJOR.MINOR.PATCH` version. The server only validates its own configuration with
 * this type, so the accepted format is deliberately narrow (ADR-17, ADR-18); clients use their own,
 * more lenient comparison.
 */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {

    init {
        require(major >= 0 && minor >= 0 && patch >= 0) { "Version components must not be negative" }
    }

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        val ZERO = AppVersion(0, 0, 0)

        // Up to nine digits per component keeps every component inside Int range.
        private const val COMPONENT = "(0|[1-9][0-9]{0,8})"
        private val FORMAT = Regex("^$COMPONENT\\.$COMPONENT\\.$COMPONENT$")

        /** Returns the parsed version, or `null` when [raw] is not exactly three numeric components. */
        fun parse(raw: String): AppVersion? {
            val groups = FORMAT.matchEntire(raw)?.groupValues ?: return null
            return AppVersion(groups[1].toInt(), groups[2].toInt(), groups[3].toInt())
        }
    }
}

package com.anksoft.kmpdemo.server.consent.repository

import com.anksoft.kmpdemo.server.consent.domain.ConsentTexts

interface ConsentTextRepository {
    /** The highest published text version, or null when none is published. */
    suspend fun latestVersion(): Int?

    suspend fun find(version: Int, language: String): ConsentTexts?

    /** True when a version above [version] is flagged `requires_reconsent`. */
    suspend fun hasReconsentAfter(version: Int): Boolean
}

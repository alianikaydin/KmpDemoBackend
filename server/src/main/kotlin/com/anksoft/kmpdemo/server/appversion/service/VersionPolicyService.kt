package com.anksoft.kmpdemo.server.appversion.service

import com.anksoft.kmpdemo.contract.app.AppPlatforms
import com.anksoft.kmpdemo.server.appversion.domain.VersionPolicy
import org.slf4j.LoggerFactory

/** Serves the configured version policy per platform. The policies are fixed for the lifetime of the process. */
class VersionPolicyService(private val policies: Map<String, VersionPolicy>) {

    private val log = LoggerFactory.getLogger(VersionPolicyService::class.java)

    /** The policy of [platform], or `null` when it is missing or not an exact platform value (no normalization, ADR-16). */
    fun policyFor(platform: String?): VersionPolicy? = platform?.let { policies[it] }

    /** Logs the effective policies once, so a forgotten configuration (all `0.0.0`) does not stay silent. */
    fun logEffectivePolicies() {
        val android = policies.getValue(AppPlatforms.ANDROID)
        val ios = policies.getValue(AppPlatforms.IOS)
        log.info(
            "Version policy: {} min={} latest={}, {} min={} latest={}",
            AppPlatforms.ANDROID, android.minSupported, android.latest,
            AppPlatforms.IOS, ios.minSupported, ios.latest,
        )
    }
}

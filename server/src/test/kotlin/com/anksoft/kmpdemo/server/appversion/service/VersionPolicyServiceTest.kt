package com.anksoft.kmpdemo.server.appversion.service

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.anksoft.kmpdemo.contract.app.AppPlatforms
import com.anksoft.kmpdemo.server.appversion.domain.AppVersion
import com.anksoft.kmpdemo.server.appversion.domain.VersionPolicy
import com.anksoft.kmpdemo.server.support.LogCapture
import kotlin.test.Test

class VersionPolicyServiceTest {

    private val android = VersionPolicy(AppVersion(1, 0, 0), AppVersion(1, 2, 0))
    private val ios = VersionPolicy(AppVersion(2, 0, 0), AppVersion(2, 5, 1))
    private val service = VersionPolicyService(mapOf(AppPlatforms.ANDROID to android, AppPlatforms.IOS to ios))

    @Test
    fun `each platform gets its own policy`() { // AC-26
        assertThat(service.policyFor("android")).isEqualTo(android)
        assertThat(service.policyFor("ios")).isEqualTo(ios)
    }

    @Test
    fun `missing and unknown platforms have no policy and are not normalized`() { // AC-26
        listOf(null, "", "web", "Android", "IOS", " ios", "ios ").forEach { platform ->
            assertThat(service.policyFor(platform), "platform=$platform").isNull()
        }
    }

    @Test
    fun `effective policies are logged as one info line`() { // ADR-17
        LogCapture().use { logs ->
            service.logEffectivePolicies()

            val lines = logs.text().lines().filter { it.contains("Version policy:") }
            assertThat(lines.size).isEqualTo(1)
            assertThat(lines.single()).contains("android min=1.0.0 latest=1.2.0")
            assertThat(lines.single()).contains("ios min=2.0.0 latest=2.5.1")
        }
    }
}

package com.anksoft.kmpdemo.server.appversion.domain

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlin.test.Test

class AppVersionTest {

    @Test
    fun `valid versions are parsed into their components`() { // AC-26
        assertThat(AppVersion.parse("0.0.0")).isEqualTo(AppVersion.ZERO)
        assertThat(AppVersion.parse("1.2.3")).isEqualTo(AppVersion(1, 2, 3))
        assertThat(AppVersion.parse("10.20.30")).isEqualTo(AppVersion(10, 20, 30))
        assertThat(AppVersion.parse("999999999.0.0")).isEqualTo(AppVersion(999_999_999, 0, 0))
    }

    @Test
    fun `malformed versions return null instead of throwing`() { // R3
        val invalid = listOf(
            "", "1", "1.2", "1.2.3.4", "v1.2.3", "01.2.3", "1.02.3", "1.2.03", "1.2.3-beta", "1..3",
            "-1.0.0", "1000000000.0.0", " 1.2.3", "1.2.3 ", "1.2.3\n", "1.2.x", "1,2,3", "+1.2.3", "１.２.３",
        )
        invalid.forEach { raw -> assertThat(AppVersion.parse(raw), "parse(\"$raw\")").isNull() }
    }

    @Test
    fun `components are compared numerically and in order`() {
        assertThat(AppVersion(1, 10, 0)).isGreaterThan(AppVersion(1, 9, 0))
        assertThat(AppVersion(2, 0, 0)).isGreaterThan(AppVersion(1, 99, 99))
        assertThat(AppVersion(1, 2, 4)).isGreaterThan(AppVersion(1, 2, 3))
        assertThat(AppVersion(1, 2, 3)).isLessThan(AppVersion(1, 3, 0))
        assertThat(AppVersion.parse("1.2.3")!!.compareTo(AppVersion(1, 2, 3))).isEqualTo(0)
    }

    @Test
    fun `toString round trips through parse`() {
        listOf("0.0.0", "1.2.3", "999999999.999999999.999999999").forEach { raw ->
            assertThat(AppVersion.parse(raw)).isNotNull().transform { it.toString() }.isEqualTo(raw)
        }
    }

    @Test
    fun `policy with min above latest is rejected`() { // R3
        assertFailure { VersionPolicy(AppVersion(2, 0, 0), AppVersion(1, 9, 9)) }
            .isInstanceOf(IllegalArgumentException::class)
    }

    @Test
    fun `policy accepts min equal to latest and the no update default`() {
        assertThat(VersionPolicy(AppVersion(1, 0, 0), AppVersion(1, 0, 0)).latest).isEqualTo(AppVersion(1, 0, 0))
        assertThat(VersionPolicy.NO_UPDATE).isEqualTo(VersionPolicy(AppVersion.ZERO, AppVersion.ZERO))
    }
}

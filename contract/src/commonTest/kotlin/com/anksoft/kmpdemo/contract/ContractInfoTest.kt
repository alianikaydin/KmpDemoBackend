package com.anksoft.kmpdemo.contract

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class ContractInfoTest {
    @Test
    fun `api version is v1`() {
        assertThat(ContractInfo.API_VERSION).isEqualTo("v1")
    }
}

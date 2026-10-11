package com.anksoft.kmpdemo.server.appversion.routes

import com.anksoft.kmpdemo.contract.app.VersionPolicyDto
import com.anksoft.kmpdemo.server.appversion.domain.VersionPolicy

internal fun VersionPolicy.toDto() = VersionPolicyDto(minSupported.toString(), latest.toString())

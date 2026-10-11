package com.anksoft.kmpdemo.server.appversion.domain

/**
 * Version policy of one platform: an installed version below [minSupported] must update,
 * one below [latest] may update.
 */
data class VersionPolicy(val minSupported: AppVersion, val latest: AppVersion) {

    init {
        require(minSupported <= latest) { "minSupported ($minSupported) must not exceed latest ($latest)" }
    }

    companion object {
        /** Neither a forced nor an optional update for any installed version. */
        val NO_UPDATE = VersionPolicy(AppVersion.ZERO, AppVersion.ZERO)
    }
}

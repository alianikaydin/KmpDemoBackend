package com.anksoft.kmpdemo.contract.app

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Version policy of one platform, returned by `GET app/version-policy`.
 * Both values are always `MAJOR.MINOR.PATCH` with numeric components and
 * [minSupportedVersion] <= [latestVersion]. `0.0.0` means "no update".
 *
 * The endpoint is public: clients send no Authorization header, and no
 * response of this path (401 included) says anything about the session.
 */
@Serializable
public data class VersionPolicyDto(
    @SerialName("min_supported_version") val minSupportedVersion: String,
    @SerialName("latest_version") val latestVersion: String,
)

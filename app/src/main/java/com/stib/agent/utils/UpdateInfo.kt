package com.stib.agent.utils

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String,
    val forceUpdate: Boolean = false,
    val minSupportedVersion: Int = 1
)

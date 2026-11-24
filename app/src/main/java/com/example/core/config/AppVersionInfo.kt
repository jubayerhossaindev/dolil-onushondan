package com.example.core.config

import com.example.BuildConfig

object AppVersionInfo {
    val versionName: String
        get() = BuildConfig.VERSION_NAME

    val versionCode: Int
        get() = BuildConfig.VERSION_CODE

    val formattedVersion: String
        get() = "v${BuildConfig.VERSION_NAME}"
}

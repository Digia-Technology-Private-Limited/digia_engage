package com.digia.engage.internal.analytics

import android.os.Build

internal object AnalyticsDeviceInfo {
    fun deviceMake(): String = Build.MANUFACTURER ?: ""
    fun deviceModel(): String = Build.MODEL ?: ""
    fun osVersion(): String = "${Build.VERSION.RELEASE} (Android)"
    fun platform(): String = "android"
}

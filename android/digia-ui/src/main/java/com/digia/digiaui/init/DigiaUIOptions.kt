package com.digia.digiaui.init

import android.content.Context
import com.digia.digiaui.framework.analytics.DUIAnalytics
import com.digia.digiaui.network.NetworkConfiguration
import com.digia.digiaui.remoteconfig.RemoteConfigProvider
import com.digia.digiaui.utils.DeveloperConfig

internal data class DigiaUIOptions(
        val context: Context,
        val accessKey: String,
        val environment: Environment = Environment.Production,
        val flavor: Flavor =
                Flavor.Debug(), // Default to Debug flavor which doesn't require extra params
        val analytics: DUIAnalytics? = null,
        val themeMode: ThemeMode= ThemeMode.SYSTEM,
        val networkConfiguration: NetworkConfiguration? = null,
        val developerConfig: DeveloperConfig = DeveloperConfig(),
        val remoteConfigProvider: RemoteConfigProvider? = null
)

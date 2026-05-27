package com.digia.engage.init

import android.content.Context
import com.digia.engage.framework.analytics.DUIAnalytics
import com.digia.engage.network.NetworkConfiguration
import com.digia.engage.remoteconfig.RemoteConfigProvider
import com.digia.engage.utils.DeveloperConfig

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

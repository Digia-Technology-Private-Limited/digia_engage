package com.digia.digiaui.config

import com.digia.digiaui.config.source.AssetConfigSource
import com.digia.digiaui.config.source.CachedConfigSource
import com.digia.digiaui.config.source.ConfigSource
import com.digia.digiaui.config.source.DelegatedConfigSource
//import com.digia.digiaui.config.source.FallbackConfigSource
import com.digia.digiaui.config.source.NetworkConfigSource
import com.digia.digiaui.config.source.NetworkFileConfigSource
import com.digia.digiaui.framework.logging.Logger
import com.digia.digiaui.init.CacheFirstStrategy
import com.digia.digiaui.init.Flavor
import com.digia.digiaui.init.LocalFirstStrategy
import com.digia.digiaui.init.NetworkFirstStrategy

/**
 * Factory for creating configuration sources based on flavor.
 *
 * This follows the Flutter ConfigStrategyFactory pattern, selecting the appropriate configuration
 * loading strategy based on the build flavor.
 *
 * Strategies:
 * - **Debug**: Network source with optional branch name
 * - **Staging**: Network staging endpoint
 * - **Versioned**: Network with version header
 * - **Release**: Complex strategy with cache/network/asset fallbacks
 */
object ConfigStrategyFactory {

    /**
     * Creates the appropriate ConfigSource based on the flavor
     *
     * @param flavor The build flavor
     * @param provider The ConfigProvider for loading operations
     * @return ConfigSource appropriate for the flavor
     */
    fun createStrategy(flavor: Flavor, provider: ConfigFetcher): ConfigSource {
        Logger.log("Creating config strategy for flavor: ${flavor.value}")

        return when (flavor) {
            is Flavor.Debug -> createDebugConfigSource(provider, flavor.branchName)
            is Flavor.Staging -> createStagingConfigSource(provider)
            is Flavor.Versioned -> createVersionedSource(provider, flavor.version)
            is Flavor.Release ->
                    createReleaseFlavorConfigSource(
                            provider,
                            flavor.initStrategy,
                            flavor.appConfigPath,
                            flavor.functionsPath
                    )
        }
    }

    /** Creates a debug configuration source with optional branch name */
    private fun createDebugConfigSource(
        provider: ConfigFetcher,
        branchName: String?
    ): ConfigSource {
        provider.addBranchName(branchName)
        return NetworkConfigSource(provider, "/config/getAppConfig")
    }

    /** Creates a staging configuration source */
    private fun createStagingConfigSource(provider: ConfigFetcher): ConfigSource {
        return NetworkConfigSource(provider, "/config/getAppConfigStaging")
    }

    /** Creates a versioned configuration source */
    private fun createVersionedSource(provider: ConfigFetcher, version: Int): ConfigSource {
        provider.addVersionHeader(version)
        return NetworkConfigSource(provider, "/config/getAppConfigForVersion")
    }

    /**
     * Creates a release flavor configuration source with multiple fallback strategies
     *
     * This implements complex logic matching Flutter's release strategy:
     * - Network-first: Try network, fall back to cache or burned assets
     * - Cache-first: Use cache, update in background
     * - Local-first: Use burned assets only
     */
    private fun createReleaseFlavorConfigSource(
        provider: ConfigFetcher,
        priority: com.digia.digiaui.init.DSLInitStrategy,
        appConfigPath: String,
        functionsPath: String
    ): ConfigSource {
        return when (priority) {
            is NetworkFirstStrategy ->
                    DelegatedConfigSource {
                        // Load burned config first
                        val burnedSource = AssetConfigSource(provider, appConfigPath, functionsPath)
                        var config = burnedSource.getConfig()

                        // Try to load from cache
                        try {
                            val cachedSource = CachedConfigSource(provider, "appConfig.json")
                            val cachedConfig = cachedSource.getConfig()

                            // Use cached if newer
                            if ((cachedConfig.version ?: 0) >= (config.version ?: 0)) {
                                config = cachedConfig
                            }
                        } catch (e: Exception) {
                            Logger.log("No valid cache found, using burned config")
                        }

                        // Add version header if available
                        config.version?.let { provider.addVersionHeader(it) }

                        // Try to get latest from network
                        try {
                            val networkSource =
                                    NetworkFileConfigSource(
                                            provider,
                                            "/config/getAppConfigRelease",
                                            timeout = priority.timeout.toLong()
                                    )
                            config = networkSource.getConfig()
                        } catch (e: Exception) {
                            Logger.log("Network update failed, using existing config")
                        }

                        config
                    }
            is CacheFirstStrategy ->
                    DelegatedConfigSource {
                        // Load burned config first
                        val burnedSource = AssetConfigSource(provider, appConfigPath, functionsPath)
                        var config = burnedSource.getConfig()

                        // Try to load from cache
                        try {
                            val cachedSource = CachedConfigSource(provider, "appConfig.json")
                            val cachedConfig = cachedSource.getConfig()

                            // Use cached if newer
                            if ((cachedConfig.version ?: 0) >= (config.version ?: 0)) {
                                config = cachedConfig
                            }
                        } catch (e: Exception) {
                            Logger.log("No valid cache found, using burned config")
                        }

                        // Update in background (fire and forget)
                        // TODO: Implement background update

                        config
                    }
            is LocalFirstStrategy -> {
                AssetConfigSource(provider, appConfigPath, functionsPath)
            }
        }
    }
}

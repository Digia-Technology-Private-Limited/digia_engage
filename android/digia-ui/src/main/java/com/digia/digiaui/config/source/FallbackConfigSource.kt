package com.digia.digiaui.config.source

import com.digia.digiaui.config.ConfigException
import com.digia.digiaui.config.model.DUIConfig
import com.digia.digiaui.framework.logging.Logger

/**
 * ConfigSource that tries multiple sources with fallback
 *
 * This source attempts to load configuration from a primary source first. If that fails,
 * it tries each fallback source in order. If all sources fail, it throws an exception.
 *
 * This provides resilience by allowing multiple configuration loading strategies.
 *
 * @param primary The primary ConfigSource to try first
 * @param fallback List of fallback ConfigSources to try if primary fails
 */
class FallbackConfigSource(
        private val primary: ConfigSource,
        private val fallback: List<ConfigSource> = emptyList()
) : ConfigSource {

    override suspend fun getConfig(): DUIConfig {
        Logger.log("Trying primary config source...")

        // Try primary source first
        try {
            return primary.getConfig()
        } catch (e: Exception) {
            Logger.log("Primary config source failed: ${e.message}")
        }

        // Try fallback sources in order
        for ((index, source) in fallback.withIndex()) {
            Logger.log("Trying fallback config source ${index + 1}...")
            try {
                return source.getConfig()
            } catch (e: Exception) {
                Logger.log("Fallback config source ${index + 1} failed: ${e.message}")
                continue
            }
        }

        // All sources failed
        throw ConfigException("All config sources failed")
    }
}

package com.digia.digiaui.config.source

import com.digia.digiaui.config.model.DUIConfig

/**
 * Abstract interface for configuration data sources.
 *
 * This follows the Flutter ConfigSource pattern, defining the contract for loading configuration
 * from various sources (network, cache, assets, etc.).
 *
 * Implementations should handle:
 * - Configuration loading from their specific source
 * - Error handling with appropriate ConfigException types
 * - Timeout management where applicable
 * - Data validation and parsing
 */
interface ConfigSource {
    /**
     * Loads the application configuration from this source.
     *
     * @return The loaded DUIConfig
     * @throws com.digia.digiaui.config.ConfigException if loading fails
     */
    suspend fun getConfig(): DUIConfig
}
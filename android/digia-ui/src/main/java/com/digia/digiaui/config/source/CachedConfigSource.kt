package com.digia.digiaui.config.source

import com.digia.digiaui.config.ConfigErrorType
import com.digia.digiaui.config.ConfigException
import com.digia.digiaui.config.ConfigFetcher
import com.digia.digiaui.config.model.DUIConfig
import com.digia.digiaui.framework.logging.Logger
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * ConfigSource that loads configuration from cached file
 *
 * This reads previously downloaded and cached configuration from the app's cache directory. Used in
 * Release builds to provide fast startup with potentially newer config than burned assets.
 *
 * @param provider The ConfigProvider (used for fileOps)
 * @param cachedFilePath The path to the cached config file
 */
class CachedConfigSource(
    private val provider: ConfigFetcher,
    private val cachedFilePath: String
) : ConfigSource {

    override suspend fun getConfig(): DUIConfig {
        try {
            Logger.log("Loading config from cache: $cachedFilePath")

            // Read cached file using fileOps
            val cachedJson = provider.fileOps.readString(cachedFilePath)
            if (cachedJson == null) {
                throw ConfigException("No cached config found")
            }

            // Parse the JSON and create DUIConfig
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val jsonData = Gson().fromJson<Map<String, Any>>(cachedJson, type)
            val config = DUIConfig.fromMap(jsonData)

            // Initialize functions
            config.functionsFilePath?.let { functionsPath ->
                try {
                    provider.initFunctions(
                            remotePath = functionsPath,
                            version = config.version
                    )
                } catch (e: Exception) {
                    Logger.log("Failed to initialize functions from cached config: $functionsPath")
                }
            }

            Logger.log("Successfully loaded config from cache (version: ${config.version})")
            return config
        } catch (e: ConfigException) {
            throw e
        } catch (e: Exception) {
            throw ConfigException(
                    "Failed to load config from cache",
                    type = ConfigErrorType.CACHE,
                    originalError = e,
                    stackTrace = e.stackTraceToString()
            )
        }
    }
}

package com.digia.digiaui.config.source

import com.digia.digiaui.config.ConfigException
import com.digia.digiaui.config.ConfigFetcher
import com.digia.digiaui.config.model.DUIConfig
import com.digia.digiaui.framework.logging.Logger
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * ConfigSource that loads configuration from network with file download and caching
 *
 * This source first fetches metadata from the network, checks if a new config should be downloaded,
 * and conditionally downloads and caches the full config file. Used for Versioned flavor.
 *
 * @param provider The ConfigProvider to handle network requests
 * @param networkPath The API endpoint path for metadata
 * @param cacheFilePath Path to cache the downloaded config file
 * @param timeout Optional timeout for network operations
 */
class NetworkFileConfigSource(
    private val provider: ConfigFetcher,
    private val networkPath: String,
    private val cacheFilePath: String = "appConfig.json",
    private val timeout: Long = 3000
) : ConfigSource {

    override suspend fun getConfig(): DUIConfig {
        try {
            Logger.log("Loading config from network file source: $networkPath")

            // 1. Get file metadata from network
            val metadata = getConfigMetadata()
            if (!shouldDownloadNewConfig(metadata)) {
                return loadCachedConfig()
            }

            // 2. Download and cache the file
            val fileUrl = getFileUrl(metadata)
            val config = downloadAndCacheConfig(fileUrl)

            // 3. Initialize functions
            config.functionsFilePath?.let { functionsPath ->
                try {
                    provider.initFunctions(
                            remotePath = functionsPath,
                            version = config.version
                    )
                } catch (e: Exception) {
                    Logger.log("Failed to initialize functions: $functionsPath")
                }
            }

            Logger.log("Successfully loaded config from network file")
            return config
        } catch (e: ConfigException) {
            throw e
        } catch (e: Exception) {
            throw ConfigException(
                    "Failed to load config from network file",
                    type = com.digia.digiaui.config.ConfigErrorType.NETWORK,
                    originalError = e,
                    stackTrace = e.stackTraceToString()
            )
        }
    }

    /** Extracts the file URL from metadata */
    private fun getFileUrl(metadata: Map<String, Any>): String {
        val fileUrl = metadata["appConfigFileUrl"] as? String
        if (fileUrl == null) {
            throw ConfigException("Config File URL not found")
        }
        return fileUrl
    }

    /** Gets config metadata from network */
    private suspend fun getConfigMetadata(): Map<String, Any> {
        val data = provider.getAppConfigFromNetwork(networkPath)
        if (data == null || data.isEmpty()) {
            throw ConfigException("Failed to fetch config metadata")
        }
        return data
    }

    /** Determines if a new config should be downloaded */
    private fun shouldDownloadNewConfig(metadata: Map<String, Any>): Boolean {
        return metadata["versionUpdated"] != false
    }

    /** Loads cached config from file */
    private suspend fun loadCachedConfig(): DUIConfig {
        val cachedJson = provider.fileOps.readString(cacheFilePath)
        if (cachedJson == null) {
            throw ConfigException("No cached config found")
        }
        val type = object : TypeToken<Map<String, Any>>() {}.type
        val jsonData = Gson().fromJson<Map<String, Any>>(cachedJson, type)
        return DUIConfig.fromMap(jsonData)
    }

    /** Downloads and caches the config file */
    private suspend fun downloadAndCacheConfig(fileUrl: String): DUIConfig {
        Logger.log("Downloading config from: $fileUrl")

        // Download file to memory (not to file, since we need to parse it)
        val response = provider.downloadOps.downloadFile(fileUrl, cacheFilePath)
        if (response == null) {
            throw ConfigException("Failed to download config file")
        }

        // For OkHttp Response, we need to read the body
        val responseBody = response.body
        if (responseBody == null) {
            throw ConfigException("Failed to download config file - empty response")
        }

        // Parse the downloaded content
        val fileBytes = responseBody.bytes()
        val fileString = String(fileBytes, Charsets.UTF_8)
        val type = object : TypeToken<Map<String, Any>>() {}.type
        val jsonData = Gson().fromJson<Map<String, Any>>(fileString, type)
        val config = DUIConfig.fromMap(jsonData)

        // Cache the file content
        try {
            val cacheSuccess = provider.fileOps.writeStringToFile(fileString, cacheFilePath)
            if (cacheSuccess) {
                Logger.log("Config cached successfully to: $cacheFilePath")
            } else {
                Logger.log("Failed to cache config file: write failed")
            }
        } catch (e: Exception) {
            Logger.log("Failed to cache config file: ${e.message}")
            // Don't throw - caching is optional
        }

        return config
    }
}

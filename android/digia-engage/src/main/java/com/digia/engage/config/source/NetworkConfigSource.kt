package com.digia.engage.config.source

import com.digia.engage.config.ConfigErrorType
import com.digia.engage.config.ConfigException
import com.digia.engage.config.ConfigFetcher
import com.digia.engage.config.model.DUIConfig
import com.digia.engage.framework.logging.Logger
import kotlin.time.Duration

/**
 * ConfigSource that loads configuration from network API
 *
 * This is the primary source for Debug, Staging, and Versioned flavors. Makes a POST request to the
 * specified endpoint to fetch the app config.
 *
 * @param provider The ConfigProvider to handle network requests
 * @param networkPath The API endpoint path (e.g., "/config/getAppConfig")
 * @param timeout Optional timeout for the network request
 */
class NetworkConfigSource(
    private val provider: ConfigFetcher,
    private val networkPath: String,
    private val timeout: Duration? = null
) : ConfigSource {

    override suspend fun getConfig(): DUIConfig {
        try {
            Logger.log("Loading config from network: $networkPath")

            val networkData = provider.getAppConfigFromNetwork(networkPath)
            if (networkData == null) {
                throw ConfigException(
                        "Network response is null",
                        type = ConfigErrorType.NETWORK
                )
            }

            // Create DUIConfig from the network data
            val appConfig = DUIConfig.fromMap(networkData)

            // Initialize functions using the remote path from config
            appConfig.functionsFilePath?.let { functionsPath ->
                try {
                    provider.initFunctions(remotePath = functionsPath)
                } catch (e: Exception) {
                    Logger.log("Failed to initialize functions from remote path: $functionsPath")
                }
            }

            Logger.log("Successfully loaded config from network")
            return appConfig
        } catch (e: ConfigException) {
            throw e
        } catch (e: Exception) {
            throw ConfigException(
                    "Failed to load config from network",
                    type = ConfigErrorType.NETWORK,
                    originalError = e,
                    stackTrace = e.stackTraceToString()
            )
        }
    }
}

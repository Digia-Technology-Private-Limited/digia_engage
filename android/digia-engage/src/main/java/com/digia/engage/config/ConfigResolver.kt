package com.digia.engage.config

import android.content.Context
import com.digia.engage.config.model.DUIConfig
import com.digia.engage.core.functions.JSFunctions
import com.digia.engage.core.functions.MobileJSFunctions
import com.digia.engage.core.functions.PreferLocal
import com.digia.engage.core.functions.PreferRemote
import com.digia.engage.framework.logging.Logger
import com.digia.engage.init.Flavor
import com.digia.engage.network.HttpMethod
import com.digia.engage.network.NetworkClient
import com.digia.engage.utils.AssetBundleOperations
import com.digia.engage.utils.AssetBundleOperationsImpl
import com.digia.engage.utils.DownloadOperations
import com.digia.engage.utils.FileDownloaderImpl
import com.digia.engage.utils.FileOperations
import com.digia.engage.utils.FileOperationsImpl

/**
 * Main configuration resolver that manages loading configuration from various sources.
 *
 * This class follows the Flutter ConfigResolver pattern, orchestrating the process of loading
 * configuration using the appropriate strategy based on the flavor.
 *
 * Key responsibilities:
 * - Managing the ConfigProvider implementation
 * - Creating appropriate ConfigSource based on Flavor
 * - Handling configuration loading failures with fallbacks
 * - Managing branch names and version headers
 *
 * Example usage:
 * ```kotlin
 * val resolver = ConfigResolver(flavor, networkClient)
 * val config = resolver.getConfig()
 * ```
 */
class ConfigResolver(
    private val _flavorInfo: Flavor,
    private val _networkClient: NetworkClient,
    private val context: Context
) : ConfigFetcher {

    private var _branchName: String? = null
    private var _functions: JSFunctions? = null

    /**
     * Loads the application configuration using the appropriate strategy
     *
     * @return The loaded DUIConfig
     * @throws ConfigException if all loading attempts fail
     */
    suspend fun getConfig(): DUIConfig {
        Logger.log("Loading config for flavor: ${_flavorInfo.value}")

        try {
            // Create the appropriate config source for this flavor
            val strategy = ConfigStrategyFactory.createStrategy(_flavorInfo, this)

            // Load the config
            val config = strategy.getConfig()

            config.jsFunctions = _functions

            Logger.log("Config loaded successfully (version: ${config.version})")
            return config
        } catch (e: ConfigException) {
            Logger.log("Config loading failed: ${e.message}")
            throw ConfigException.networkError("Failed to load configuration: ${e.message}", e)
        }
    }

    /**
     * Fetches application configuration from the network API
     *
     * @param path The API endpoint path (e.g., "/config/getDUIConfig")
     * @return Parsed JsonLike object
     * @throws ConfigException if network request fails or parsing fails
     */
    override suspend fun getAppConfigFromNetwork(path: String): Map<String, Any>? {
        try {
            Logger.log("Fetching config from network: $path")

            // Make POST request to get config
            val response =
                    _networkClient.requestInternal<Map<String, Any>>(
                            method = HttpMethod.POST,
                            path = path,
                            fromJsonT = { it as? Map<String, Any> ?: emptyMap() },
                            data = mapOf("branchName" to _branchName)
                    )

            if (response.isSuccess == true && response.data != null) {
                // Get the response field
                val data = response.data["response"] as? Map<String, Any>
                Logger.log("Network config fetched successfully")
                return data
            } else {
                throw ConfigException.networkError("Network request failed: ${response.error}")
            }
        } catch (e: Exception) {
            Logger.log("Network config fetch failed: ${e.message}")
            throw ConfigException.networkError(
                    "Failed to fetch config from network: ${e.message}",
                    e
            )
        }
    }

    /** Initializes custom JavaScript functions */
    override suspend fun initFunctions(remotePath: String?, localPath: String?, version: Int?) {
        _functions = MobileJSFunctions(context)
        if (remotePath != null) {
            val res = _functions!!.initFunctions(PreferRemote(remotePath, version))
            if (!res) {
                throw ConfigException("Functions not initialized")
            }
        }
        if (localPath != null) {
            val res = _functions!!.initFunctions(PreferLocal(localPath))
            if (!res) {
                throw ConfigException("Functions not initialized")
            }
        }
    }

    /** Adds branch name to request headers for debug builds */
    override fun addBranchName(branchName: String?) {
        _branchName = branchName
        Logger.log("Branch name set: $branchName")
    }

    /** Adds version header to requests for versioned builds */
    override fun addVersionHeader(version: Int) {
        _networkClient.addVersionHeader(version)
    }

    // Initialize operations
    override val bundleOps: AssetBundleOperations by lazy {
        AssetBundleOperationsImpl(context.assets)
    }

    override val fileOps: FileOperations by lazy {
        FileOperationsImpl()
    }

    override val downloadOps: DownloadOperations by lazy {
        FileDownloaderImpl()
    }
}

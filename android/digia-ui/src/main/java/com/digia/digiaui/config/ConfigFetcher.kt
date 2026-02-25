package com.digia.digiaui.config

import com.digia.digiaui.utils.DownloadOperations
import com.digia.digiaui.utils.AssetBundleOperations
import com.digia.digiaui.utils.FileOperations

/**
 * Abstract interface for configuration loading operations.
 *
 * This interface provides methods for loading configuration from network, initializing functions,
 * and accessing file operations. It follows the Flutter ConfigProvider pattern.
 */
interface ConfigFetcher {
    /**
     * Gets application configuration from the network
     *
     * @param path The API path to fetch configuration from
     * @return JsonLike object
     * @throws ConfigException if request fails
     */
    suspend fun getAppConfigFromNetwork(path: String): Map<String, Any>?

    /**
     * Initializes custom JavaScript functions (placeholder for future implementation)
     *
     * @param remotePath Path to remote functions file
     * @param localPath Path to local functions file
     * @param version Version number for remote functions
     */
    suspend fun initFunctions(
            remotePath: String? = null,
            localPath: String? = null,
            version: Int? = null
    )

    /**
     * Adds branch name to request headers (for debug builds)
     *
     * @param branchName The git branch name
     */
    fun addBranchName(branchName: String?)

    /**
     * Adds version header to requests (for versioned builds)
     *
     * @param version The app version number
     */
    fun addVersionHeader(version: Int)


    /** Gets the asset bundle operations for reading bundled assets */
    val bundleOps: AssetBundleOperations

    /** Gets the file operations for reading/writing local files */
    val fileOps: FileOperations

    /** Gets the file downloader for downloading files from network */
    val downloadOps: DownloadOperations
}

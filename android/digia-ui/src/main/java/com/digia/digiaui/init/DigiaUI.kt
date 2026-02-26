package com.digia.digiaui.init

import com.digia.digiaui.config.ConfigResolver
import com.digia.digiaui.config.model.DUIConfig
import com.digia.digiaui.framework.logging.Logger
import com.digia.digiaui.framework.preferences.PreferencesStore
import com.digia.digiaui.network.NetworkClient
import com.digia.digiaui.network.NetworkConfiguration
import com.digia.digiaui.utils.HostApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class DigiaUI
private constructor(
        /** The initialization configuration provided during SDK setup. */
        val initConfig: DigiaUIOptions,

        /** The network client used for API communications. */
        val networkClient: NetworkClient,

        /** The DSL configuration containing widget definitions and app structure. */
        val dslConfig: DUIConfig
) {

    companion object {
        /**
         * Initializes the Digia UI SDK with the provided configuration.
         *
         * This is the main initialization method that sets up the SDK for use.
         * It performs the following operations:
         * - Initializes shared preferences store
         * - Creates network client with proper headers
         * - Loads configuration from the server
         * - Sets up state observers if provided
         *
         * @param options Contains all the configuration needed for initialization
         * @return A fully initialized [DigiaUI] instance ready for use
         * @throws Exception if initialization fails (network errors, invalid config, etc.)
         */
        internal suspend fun initialize(options: DigiaUIOptions): DigiaUI =
                withContext(Dispatchers.IO) {
                    try {
                        Logger.log("Initializing Digia UI SDK...")

                        // Initialize Preferences
                        PreferencesStore.initialize(options.context)
                        Logger.log("Preferences initialized")

                        // Create network headers
                        val headers = createDigiaHeaders(options, uuid = null)
                        Logger.log("Headers created")

                        // Create network client
                        val baseUrl = options.developerConfig?.baseUrl ?: ""

                        val networkConfig =
                                options.networkConfiguration
                                        ?: NetworkConfiguration.withDefaults()

                        val networkClient =
                                NetworkClient(
                                        baseUrl = baseUrl,
                                        digiaHeaders = headers,
                                        projectNetworkConfiguration = networkConfig,
                                        developerConfig = options.developerConfig,
                                )
                        Logger.log("Network client created with baseUrl: $baseUrl")

                        // Load configuration using resolver
                        val configResolver =
                                ConfigResolver(
                                        _flavorInfo = options.flavor,
                                        _networkClient = networkClient,
                                        context = options.context
                                )

                        val config = configResolver.getConfig()
                        Logger.log(
                                "Configuration loaded successfully (version: ${config.version})"
                        )

                        DigiaUI(options, networkClient, config)
                    } catch (e: Exception) {
                        Logger.log("Failed to initialize Digia UI: ${e.message}")
                        throw e
                    }
                }

        /**
         * Creates the default headers required for Digia API communication.
         *
         * This method generates headers containing SDK version, platform information,
         * app details, and authentication information.
         *
         * @param options Contains the configuration including access key
         * @param uuid Optional user/device identifier
         * @return A map of headers to be used with network requests
         */
        private fun createDigiaHeaders(
                options: DigiaUIOptions,
                uuid: String?
        ): Map<String, String> {
            val context = options.context
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val packageName = packageInfo.packageName
            val appVersion = packageInfo.versionName ?: ""
            val appBuildNumber =
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode.toString()
                    } else {
                        @Suppress("DEPRECATION") packageInfo.versionCode.toString()
                    }
            // Android build signature (SHA256)
            val appSignatureSha256 =
                    try {
                        val signatures =
                                if (android.os.Build.VERSION.SDK_INT >=
                                                android.os.Build.VERSION_CODES.P
                                ) {
                                    context.packageManager
                                            .getPackageInfo(
                                                    packageName,
                                                    android.content.pm.PackageManager
                                                            .GET_SIGNING_CERTIFICATES
                                            )
                                            .signingInfo
                                            ?.apkContentsSigners
                                            ?.map { it.toCharsString() }
                                            ?: emptyList()
                                } else {
                                    @Suppress("DEPRECATION")
                                    context.packageManager
                                            .getPackageInfo(
                                                    packageName,
                                                    android.content.pm.PackageManager.GET_SIGNATURES
                                            )
                                            .signatures
                                            ?.map { it.toCharsString() }
                                            ?: emptyList()
                                }
                        signatures.firstOrNull() ?: ""
                    } catch (_: Exception) {
                        ""
                    }

            return NetworkClient.getDefaultDigiaHeaders(
                    packageVersion = getSDKVersion(),
                    accessKey = options.accessKey,
                    platform = getPlatform(),
                    uuid = uuid,
                    packageName = packageName,
                    appVersion = appVersion,
                    appBuildNumber = appBuildNumber,
                    environment = options.flavor.environment.name,
                    buildSignature = appSignatureSha256,
                host = HostApp.getHostApp(options.developerConfig.host)
            )
        }

        /**
         * Determines the current platform for API communication.
         *
         * Returns platform identifier string:
         * - 'android' for Android devices
         * - 'ios' for iOS (not applicable in pure Android)
         * - 'mobile_web' for web or other platforms
         */
        private fun getPlatform(): String {
            return "android"
        }

        /**
         * Gets the current SDK version.
         *
         * @return The SDK version string
         */
        private fun getSDKVersion(): String {
            return com.digia.digiaui.DigiaUIVersion.SDK_VERSION
        }
    }
}

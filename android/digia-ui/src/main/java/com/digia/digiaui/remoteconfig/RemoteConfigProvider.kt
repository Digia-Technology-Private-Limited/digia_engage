package com.digia.digiaui.remoteconfig

/**
 * Interface for providing remote configuration data for dynamic widget loading.
 *
 * This abstraction allows developers to integrate any remote config provider
 * (Firebase Remote Config, custom backend, etc.) with Digia UI without creating
 * a direct dependency on specific third-party services.
 *
 * **Important**: This interface is synchronous. The implementation should return
 * already-fetched/cached configuration data. It's the developer's responsibility
 * to fetch and cache remote config data before calling Load.
 *
 * Example implementation with Firebase Remote Config:
 * ```kotlin
 * class FirebaseConfigProvider(private val remoteConfig: FirebaseRemoteConfig) : RemoteConfigProvider {
 *     override fun getConfig(key: String): RemoteConfigData? {
 *         return try {
 *             val jsonString = remoteConfig.getString(key)
 *             if (jsonString.isEmpty()) return null
 *             
 *             val json = JSONObject(jsonString)
 *             val componentId = json.getString("componentId")
 *             val args = json.optJSONObject("args")?.let { argsJson ->
 *                 argsJson.keys().asSequence().associateWith { argsJson.get(it) }
 *             }
 *             
 *             RemoteConfigData(componentId, args)
 *         } catch (e: Exception) {
 *             null
 *         }
 *     }
 * }
 * ```
 */
interface RemoteConfigProvider {
    /**
     * Gets remote configuration data for the specified key.
     *
     * This method should return cached/pre-fetched configuration data synchronously.
     * Do NOT perform network calls or async operations in this method.
     *
     * The returned data should contain a componentId and optional args that will be
     * used to render a dynamic widget.
     *
     * Expected remote config format:
     * ```json
     * {
     *   "componentId": "carditem-wmiNEv",
     *   "args": {
     *     "itemPrice": "999",
     *     "itemName": "Product"
     *   }
     * }
     * ```
     *
     * @param key The remote config key/slot name to fetch
     * @return RemoteConfigData containing componentId and args, or null if not found or error
     */
    fun getConfig(key: String): RemoteConfigData?
}

/**
 * Data class representing remote configuration data for a widget.
 *
 * @property componentId The unique identifier of the component to load
 * @property args Optional arguments to pass to the component
 */
data class RemoteConfigData(
    val componentId: String,
    val args: Map<String, Any?>? = null
)

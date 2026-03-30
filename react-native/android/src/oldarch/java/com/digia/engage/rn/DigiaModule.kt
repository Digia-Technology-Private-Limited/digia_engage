/**
 * DigiaModule — Old Architecture (Bridge) wrapper
 *
 * Extends ReactContextBaseJavaModule for host apps that have NOT enabled the React Native New
 * Architecture. Delegates all logic to [DigiaModuleImpl].
 *
 * When the host app's newArchEnabled property is false (or absent), build.gradle includes this
 * source set (src/oldarch/) instead of src/newarch/.
 */
package com.digia.engage.rn

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap

internal class DigiaModule(
        reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {

    private val impl = DigiaModuleImpl(reactContext)

    override fun getName(): String = DigiaModuleImpl.MODULE_NAME

    @ReactMethod
    fun initialize(apiKey: String, environment: String, logLevel: String, promise: Promise) =
            impl.initialize(apiKey, environment, logLevel, promise)

    @ReactMethod fun registerBridge() = impl.registerBridge()

    @ReactMethod fun setCurrentScreen(name: String) = impl.setCurrentScreen(name)

    @ReactMethod
    fun triggerCampaign(id: String, content: ReadableMap, cepContext: ReadableMap) =
            impl.triggerCampaign(id, content, cepContext)

    @ReactMethod fun invalidateCampaign(campaignId: String) = impl.invalidateCampaign(campaignId)
}

/**
 * DigiaModule — New Architecture (TurboModule / JSI) wrapper
 *
 * Extends the codegen-generated [NativeDigiaEngageSpec] abstract class, which
 * itself extends ReactContextBaseJavaModule.  This enables React Native to
 * resolve the module directly via JSI (no JSON bridge serialisation).
 *
 * When the host app's newArchEnabled property is "true", build.gradle includes
 * this source set (src/newarch/) instead of src/oldarch/.  The React codegen
 * plugin generates NativeDigiaEngageSpec from NativeDigiaEngage.ts.
 *
 * All business logic lives in [DigiaModuleImpl] (src/main/).
 */
package com.digia.engage.rn

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap

internal class DigiaModule(
        reactContext: ReactApplicationContext,
) : NativeDigiaEngageSpec(reactContext) {

    private val impl = DigiaModuleImpl(reactContext)

    override fun getName(): String = DigiaModuleImpl.MODULE_NAME

    override fun initialize(apiKey: String, environment: String, logLevel: String, promise: Promise) =
            impl.initialize(apiKey, environment, logLevel, promise)

    override fun registerBridge() = impl.registerBridge()

    override fun setCurrentScreen(name: String) = impl.setCurrentScreen(name)

    override fun triggerCampaign(id: String, content: ReadableMap, cepContext: ReadableMap) =
            impl.triggerCampaign(id, content, cepContext)

    override fun invalidateCampaign(campaignId: String) = impl.invalidateCampaign(campaignId)
}

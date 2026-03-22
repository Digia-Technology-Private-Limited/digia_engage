/**
 * DigiaPackage
 *
 * Registers the Digia Engage native module and view managers with React Native.
 *
 * Extends TurboReactPackage so the package works transparently on both Old Architecture (bridge)
 * and New Architecture (TurboModules / JSI).
 *
 * Why is this needed? ──────────────────── React Native's auto-linker reads react-native.config.js,
 * which declares `packageInstance: 'new DigiaPackage()'`. At host-app compile time, RN injects this
 * into the generated `PackageList`, making our native module and view managers available to JS
 * without any manual MainApplication edits.
 *
 * JS: NativeModules.DigiaEngageModule ──► DigiaModule.kt JS:
 * requireNativeComponent('DigiaHostView') ──► DigiaViewManager.kt JS:
 * requireNativeComponent('DigiaSlotView') ──► DigiaSlotViewManager.kt
 */
package com.digia.engage.rn

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider
import com.facebook.react.uimanager.ViewManager

class DigiaPackage : BaseReactPackage() {

    // ─── Native Modules ───────────────────────────────────────────────────────

    /**
     * Called by New Architecture (TurboModules) to instantiate the module by name. Old Architecture
     * continues to use createNativeModules() which TurboReactPackage implements internally by
     * delegating to this method via getReactModuleInfoProvider.
     */
    override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? =
            when (name) {
                DigiaModule.MODULE_NAME -> DigiaModule(reactContext)
                else -> null
            }

    /**
     * Metadata table: tells the RN runtime which modules this package provides and whether they
     * should be treated as TurboModules (New Architecture).
     */
    override fun getReactModuleInfoProvider(): ReactModuleInfoProvider = ReactModuleInfoProvider {
        mapOf(
                DigiaModule.MODULE_NAME to
                        ReactModuleInfo(
                                /* name */ DigiaModule.MODULE_NAME,
                                /* className */ DigiaModule.MODULE_NAME,
                                /* canOverrideExistingModule */ false,
                                /* needsEagerInit */ false,
                                /* isCxxModule */ false,
                                /* isTurboModule */ false,
                        ),
        )
    }

    // ─── View Managers ────────────────────────────────────────────────────────

    override fun createViewManagers(
            reactContext: ReactApplicationContext,
    ): List<ViewManager<*, *>> =
            listOf(
                    DigiaViewManager(),
                    DigiaSlotViewManager(),
            )
}

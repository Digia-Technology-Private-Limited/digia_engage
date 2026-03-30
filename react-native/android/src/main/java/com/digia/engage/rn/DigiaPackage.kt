/**
 * DigiaPackage
 *
 * Registers the Digia Engage native module and view managers with React Native.
 *
 * Extends BaseReactPackage so the package works transparently on both Old Architecture (bridge) and
 * New Architecture (TurboModules / JSI).
 *
 * Hybrid strategy: ──────────────── • New Architecture host (newArchEnabled=true): The DigiaModule
 * in src/newarch/ extends the codegen-generated NativeDigiaEngageSpec, and isTurboModule is set to
 * true in ReactModuleInfo. React Native resolves the module via JSI — no bridge serialisation.
 *
 * • Old Architecture host (newArchEnabled=false): The DigiaModule in src/oldarch/ extends
 * ReactContextBaseJavaModule, and isTurboModule is set to false. The module is resolved via the
 * classic bridge.
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
         * Called by the RN runtime to instantiate the module by name. On New Architecture this is
         * called via JSI; on Old Architecture via the bridge.
         */
        override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? =
                when (name) {
                        DigiaModuleImpl.MODULE_NAME -> DigiaModule(reactContext)
                        else -> null
                }

        /**
         * Metadata table: tells the RN runtime which modules this package provides and whether they
         * should be treated as TurboModules (New Architecture).
         *
         * isTurboModule is set dynamically via BuildConfig: • true when the host app builds with
         * newArchEnabled=true → JSI path • false when the host app uses Old Architecture → bridge
         * path
         */
        override fun getReactModuleInfoProvider(): ReactModuleInfoProvider =
                ReactModuleInfoProvider {
                        mapOf(
                                DigiaModuleImpl.MODULE_NAME to
                                        ReactModuleInfo(
                                                /* name */ DigiaModuleImpl.MODULE_NAME,
                                                /* className */ DigiaModuleImpl.MODULE_NAME,
                                                /* canOverrideExistingModule */ false,
                                                /* needsEagerInit */ false,
                                                /* isCxxModule */ false,
                                                /* isTurboModule */ BuildConfig
                                                        .IS_NEW_ARCHITECTURE_ENABLED,
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

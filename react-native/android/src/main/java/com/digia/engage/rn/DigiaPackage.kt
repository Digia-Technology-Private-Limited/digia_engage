/**
 * DigiaPackage
 *
 * Registers the Digia Engage native module and view manager with React Native.
 *
 * ─── Android integration ───────────────────────────────────────────────────── Add this package to
 * your `MainApplication`:
 *
 * ```kotlin
 * // android/app/src/main/java/com/yourapp/MainApplication.kt
 * override fun getPackages(): List<ReactPackage> =
 *     PackageList(this).packages.apply {
 *         add(DigiaPackage())
 *     }
 * ```
 *
 * When using React Native's auto-linking the package is registered automatically via the
 * `react-native.config.js` declared in this library.
 * ─────────────────────────────────────────────────────────────────────────────
 */
package com.digia.engage.rn

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

class DigiaPackage : ReactPackage {

    override fun createNativeModules(
            reactContext: ReactApplicationContext,
    ): List<NativeModule> = listOf(DigiaModule(reactContext))

    override fun createViewManagers(
            reactContext: ReactApplicationContext,
    ): List<ViewManager<*, *>> = listOf(DigiaViewManager(), DigiaSlotViewManager())
}

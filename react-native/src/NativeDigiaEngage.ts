/**
 * NativeDigiaModule
 *
 * Low-level TurboModule binding to the native Digia Engage module.
 *
 * The module is resolved lazily on first use (not at import time) so that
 * module evaluation before native initialisation doesn't throw.
 * Resolution order:
 *   1. TurboModuleRegistry.get()   — New Architecture / JSI path
 *   2. NativeModules               — bridge interop layer (RN 0.73+ New Arch
 *                                    with isTurboModule: false in ReactModuleInfo)
 *   3. null                        — non-Android environments; methods no-op
 *
 * Prefer using the high-level `Digia` singleton from `index.ts`.
 */
import type { TurboModule } from 'react-native';
import { NativeModules, TurboModuleRegistry } from 'react-native';

/**
 * Codegen spec — drives Android/iOS TurboModule generation.
 *
 * Rules for codegen compatibility:
 * • File must be named `Native*.ts` (already satisfied).
 * • All method parameter types must be scalar, Promise, Object, or Array.
 * • `Object` maps to ReadableMap on Android, NSDictionary on iOS.
 */
export interface Spec extends TurboModule {
    /** Initialise the SDK. Call once before anything else. */
    initialize(apiKey: string, environment: string, logLevel: string): Promise<void>;

    /**
     * Wire the internal RNEventBridgePlugin with the native SDK.
     * Called automatically by Digia.register() on first plugin registration.
     * Never call this directly.
     */
    registerBridge(): void;

    /** Notify the SDK of the currently visible screen. */
    setCurrentScreen(name: string): void;

    /**
     * Forward a campaign payload from a JS CEP plugin into the native
     * rendering engine via the DigiaCEPDelegate.
     */
    triggerCampaign(
        id: string,
        content: Object,
        cepContext: Object,
    ): void;

    /** Invalidate / dismiss a campaign by its ID. */
    invalidateCampaign(campaignId: string): void;

}

// Try TurboModuleRegistry first (New Architecture / JSI).
// Fall back to NativeModules (bridge interop layer — enabled by default in
// RN 0.73+ New Architecture when the module is registered with
// isTurboModule: false in ReactModuleInfo).
// If neither resolves, warn in DEV and use no-op stubs so non-Android
// environments (web, Storybook) don't crash.
let _resolved: Spec | null = null;
function getModule(): Spec | null {
    if (_resolved !== null) return _resolved;
    _resolved =
        TurboModuleRegistry.get<Spec>('DigiaEngageModule') ??
        (NativeModules.DigiaEngageModule as Spec | undefined) ??
        null;
    if (__DEV__ && !_resolved) {
        console.warn(
            '[Digia] DigiaEngageModule not found.\n' +
            'Make sure @digia/engage is linked and the app has been rebuilt.',
        );
    }
    return _resolved;
}

export const nativeDigiaModule: Spec = {
    initialize: (apiKey, environment, logLevel) =>
        getModule()?.initialize(apiKey, environment, logLevel) ?? Promise.resolve(),
    registerBridge: () => getModule()?.registerBridge(),
    setCurrentScreen: (name) => getModule()?.setCurrentScreen(name),
    triggerCampaign: (id, content, cepContext) =>
        getModule()?.triggerCampaign(id, content, cepContext),
    invalidateCampaign: (campaignId) => getModule()?.invalidateCampaign(campaignId),
    getConstants: () => getModule()?.getConstants?.() ?? {},
};

/**
 * NativeDigiaModule
 *
 * Low-level native binding to the Digia Engage module.
 *
 * The module is resolved lazily on first use (not at import time) so that
 * module evaluation before native initialisation doesn't throw.
 * Resolution order:
 *   1. NativeModules               — iOS bridge module
 *   2. null                        — non-Android environments; methods no-op
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
    initialize(projectId: string, environment: string, logLevel: string, baseUrl?: string): Promise<void>;

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

    /** Register a UI element as an anchor point for Guide experiences. */
    registerAnchor(key: string, x: number, y: number, width: number, height: number): void;
    unregisterAnchor(key: string): void;

    /** Return all registered components (anchors/slots) for health reporting. */
    getRegisteredComponents(): Promise<Array<{ component_key: string; component_type: 'anchor' | 'slot'; screen_name: string | null }>>;
}

let _resolved: Spec | null = null;
let _didResolve = false;

function resolveCodegenModule(): Spec | null {
    return TurboModuleRegistry.get<Spec>('DigiaEngageModule') ?? null;
}

function getModule(): Spec | null {
    if (_didResolve) return _resolved;
    _didResolve = true;

    _resolved = (NativeModules.DigiaEngageModule as Spec | undefined) ?? resolveCodegenModule();
    if (__DEV__ && !_resolved) {
        console.warn(
            '[Digia] DigiaEngageModule not found.\n' +
            'Make sure @digia/engage is linked and the app has been rebuilt.',
        );
    }
    return _resolved;
}

export const nativeDigiaModule: Spec = {
    initialize: (projectId, environment, logLevel, baseUrl) =>
        getModule()?.initialize(projectId, environment, logLevel, baseUrl) ?? Promise.resolve(),
    registerBridge: () => getModule()?.registerBridge(),
    setCurrentScreen: (name) => getModule()?.setCurrentScreen(name),
    triggerCampaign: (id, content, cepContext) =>
        getModule()?.triggerCampaign(id, content, cepContext),
    invalidateCampaign: (campaignId) => getModule()?.invalidateCampaign(campaignId),
    registerAnchor: (key, x, y, width, height) => getModule()?.registerAnchor(key, x, y, width, height),
    unregisterAnchor: (key) => getModule()?.unregisterAnchor(key),
    getRegisteredComponents: () => getModule()?.getRegisteredComponents() ?? Promise.resolve([]),
    getConstants: () => getModule()?.getConstants?.() ?? {},
};

/**
 * NativeDigiaModule
 *
 * Low-level binding to the native (Android/iOS) module.
 * Prefer using the high-level `Digia` singleton from `index.ts`.
 */
import { NativeModules, Platform } from 'react-native';

const LINKING_ERROR =
    "The package '@digia/engage-react-native' doesn't seem to be linked. " +
    'Make sure to run `npx react-native build-android` (or rebuild the app) ' +
    'after installing the package.\n\n' +
    'If you are using the New Architecture, make sure to run `npx react-native codegen` before building.';

// On Android the native module is registered as 'DigiaEngageModule'.
// iOS currently returns a no-op proxy (see ios/ folder).
const NativeDigia =
    Platform.OS === 'android'
        ? NativeModules.DigiaEngageModule
        : Platform.OS === 'ios'
            ? NativeModules.DigiaEngageModule // iOS stub
            : null;

if (!NativeDigia) {
    console.warn(LINKING_ERROR);
}

export interface NativeDigiaModule {
    /**
     * Initialise the Digia Engage SDK.
     * Must be called once before any other method, typically in your App root.
     */
    initialize(apiKey: string, environment: string, logLevel: string): Promise<void>;

    /**
     * Notify the SDK of the currently visible screen.
     * Call this whenever the navigation state changes.
     */
    setCurrentScreen(name: string): void;

    /**
     * Launch the Digia UI full-screen navigation activity (Android-only currently).
     */
    openNavigation(startPageId: string | null, pageArgs: Record<string, string>): void;

    /**
     * Push a campaign payload into Digia's rendering engine.
     *
     * Used by JS-side CEP plugins (e.g. DigiaMoEngagePlugin) to hand off a
     * campaign received from a third-party SDK into the Digia overlay system.
     */
    triggerCampaign(
        id: string,
        content: Record<string, unknown>,
        cepContext: Record<string, unknown>,
    ): void;

    /**
     * Dismiss / invalidate an active campaign by ID.
     */
    invalidateCampaign(campaignId: string): void;
}

export const nativeDigiaModule: NativeDigiaModule = NativeDigia ?? {
    initialize: () => Promise.resolve(),
    setCurrentScreen: () => { },
    openNavigation: () => { },
    triggerCampaign: () => { },
    invalidateCampaign: () => { },
};

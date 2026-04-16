/**
 * High-level Digia Engage SDK wrapper.
 *
 * Usage
 * ──────
 * ```ts
 * import { Digia } from '@digia/engage-react-native';
 *
 * // In your App entry point (e.g. App.tsx):
 * await Digia.initialize({ apiKey: 'YOUR_API_KEY' });
 *
 * // Whenever your navigation screen changes:
 * Digia.setCurrentScreen('Home');
 *
 * ```
 */

import { DeviceEventEmitter } from 'react-native';
import { nativeDigiaModule } from './NativeDigiaEngage';
import type {
    DigiaConfig,
    DigiaDelegate,
    DigiaExperienceEvent,
    DigiaPlugin,
    InAppPayload,
} from './types';

class DigiaClass implements DigiaDelegate {
    private readonly _plugins = new Map<string, DigiaPlugin>();
    // Tracks whether the native bridge plugin (RNEventBridgePlugin) has been
    // wired to the native SDK. Done once on the first Digia.register() call.
    private _nativeBridgeWired = false;
    // Cache of triggered payloads keyed by campaign ID, used to reconstruct
    // the full InAppPayload when overlay lifecycle events arrive from native.
    private readonly _activePayloads = new Map<string, InAppPayload>();
    private _engageSubscription: { remove(): void } | null = null;

    /**
     * Initialise the Digia Engage SDK.
     *
     * Must be called once before anything else – ideally as early as possible
     * in your application lifecycle (start of `App.tsx`).
     */
    async initialize(config: DigiaConfig): Promise<void> {
        const environment = config.environment ?? 'production';
        const logLevel = config.logLevel ?? 'error';
        await nativeDigiaModule.initialize(config.apiKey, environment, logLevel);
    }

    /**
     * Register a CEP plugin with the Digia SDK.
     *
     * On the first call this also wires the internal RNEventBridgePlugin with
     * the native Android SDK so it holds the DigiaCEPDelegate reference needed
     * to show overlays and emit lifecycle events back to JS. This is transparent
     * bridge plumbing — you never interact with RNEventBridgePlugin directly.
     *
     * ```ts
     * import { DigiaMoEngagePlugin } from '@digia/moengage-plugin';
     *
     * await Digia.initialize({ apiKey: 'YOUR_KEY' });
     * Digia.register(new DigiaMoEngagePlugin({ moEngage: MoEngage }));
     * ```
     */
    register(plugin: DigiaPlugin): void {
        if (this._plugins.has(plugin.identifier)) {
            this._plugins.get(plugin.identifier)!.teardown();
        }
        // Wire the native bridge plugin once, before the first plugin's setup()
        // so the delegate is ready when JS campaigns start flowing.
        if (!this._nativeBridgeWired) {
            nativeDigiaModule.registerBridge();
            this._startEngageListener();
            this._nativeBridgeWired = true;
        }
        plugin.setup(this);
        this._plugins.set(plugin.identifier, plugin);
    }

    /**
     * Unregister a previously registered plugin.
     * Calls plugin.teardown() internally.
     */
    unregister(pluginOrId: DigiaPlugin | string): void {
        const id = typeof pluginOrId === 'string' ? pluginOrId : pluginOrId.identifier;
        this._plugins.get(id)?.teardown();
        this._plugins.delete(id);
    }

    /**
     * Notify the SDK of the currently active screen name.
     *
     * Wire this up to your navigation library's screen-change listener
     * (e.g. React Navigation focus events or `useNavigationContainerRef`).
     * All registered plugins will have forwardScreen() called automatically.
     */
    setCurrentScreen(name: string): void {
        nativeDigiaModule.setCurrentScreen(name);
        this._plugins.forEach((plugin) => plugin.forwardScreen(name));
    }

    /**
     * Presents full-screen native Digia SDUI for the project initial route
     * (AppConfig initial route on native).
     */
    createInitialPage(): void {
        nativeDigiaModule.createInitialPage();
    }

    // ── DigiaDelegate ────────────────────────────────────────────────────────
    // Mirrors DigiaCEPDelegate on Android.
    // Forwards to the native DigiaCEPDelegate via the bridge.

    onCampaignTriggered(payload: InAppPayload): void {
        this._activePayloads.set(payload.id, payload);
        nativeDigiaModule.triggerCampaign(payload.id, payload.content, payload.cepContext);
    }

    onCampaignInvalidated(campaignId: string): void {
        this._activePayloads.delete(campaignId);
        nativeDigiaModule.invalidateCampaign(campaignId);
    }

    // ── Overlay event forwarding ─────────────────────────────────────────────

    /**
     * Subscribes to `digiaOverlayEvent` emitted by the native
     * RNEventBridgePlugin when the Compose overlay fires a lifecycle event
     * (impressed / clicked / dismissed).
     *
     * Each event is forwarded to every registered plugin's notifyEvent() so
     * that CEP plugins (e.g. WebEngagePlugin) can report analytics.
     */
    private _startEngageListener(): void {
        if (this._engageSubscription) return;
        this._engageSubscription = DeviceEventEmitter.addListener(
            'digiaEngageEvent',
            (data: { campaignId: string; type: string; elementId?: string }) =>
                this._forwardExperienceEvent(data),
        );
    }

    private _forwardExperienceEvent(
        data: { campaignId: string; type: string; elementId?: string },
    ): void {
        const payload = this._activePayloads.get(data.campaignId);
        if (!payload) return;

        let event: DigiaExperienceEvent;
        switch (data.type) {
            case 'impressed':
                event = { type: 'impressed' };
                break;
            case 'clicked':
                event = { type: 'clicked', elementId: data.elementId };
                break;
            case 'dismissed':
                event = { type: 'dismissed' };
                this._activePayloads.delete(data.campaignId);
                break;
            default:
                return;
        }

        this._plugins.forEach((plugin) => plugin.notifyEvent(event, payload));
    }

}

export const Digia = new DigiaClass();

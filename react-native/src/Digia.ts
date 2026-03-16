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

import { nativeDigiaModule } from './NativeDigiaModule';
import type { DigiaConfig, DigiaDelegate, DigiaPlugin, InAppPayload } from './types';

class DigiaClass implements DigiaDelegate {
    private readonly _plugins = new Map<string, DigiaPlugin>();
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
     * • Calls nativeDigiaModule.register() to register the internal
     *   RNEventBridgePlugin with the native SDK (mirrors Digia.register)
     * • Calls plugin.setup(this) to give the JS plugin a DigiaDelegate
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
        // Register the native bridge plugin so overlay events flow to JS
        nativeDigiaModule.register();
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

    // ── DigiaDelegate ────────────────────────────────────────────────────────
    // Mirrors DigiaCEPDelegate on Android.
    // Forwards to the native DigiaCEPDelegate via the bridge.

    onCampaignTriggered(payload: InAppPayload): void {
        nativeDigiaModule.triggerCampaign(payload.id, payload.content, payload.cepContext);
    }

    onCampaignInvalidated(campaignId: string): void {
        nativeDigiaModule.invalidateCampaign(campaignId);
    }

}

export const Digia = new DigiaClass();

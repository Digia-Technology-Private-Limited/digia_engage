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
 * // To open the Digia UI navigation flow (Android):
 * Digia.openNavigation({ startPageId: 'onboarding' });
 * ```
 */

import { nativeDigiaModule } from './NativeDigiaModule';
import type { DigiaConfig, DigiaNavigationOptions, DigiaPlugin } from './types';

class DigiaClass {
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
     * Calls plugin.setup() internally. Do not call plugin.setup() manually.
     *
     * ```ts
     * import MoEngage from 'react-native-moengage';
     * import { DigiaMoEngagePlugin } from '@digia/moengage-react-native';
     *
     * await Digia.initialize({ apiKey: 'YOUR_KEY' });
     * Digia.register(new DigiaMoEngagePlugin({ moEngage: MoEngage }));
     * ```
     */
    register(plugin: DigiaPlugin): void {
        if (this._plugins.has(plugin.identifier)) {
            this._plugins.get(plugin.identifier)!.teardown();
        }
        plugin.setup();
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
     * Launch the Digia UI navigation activity (Android).
     *
     * Presents the full-screen Compose navigation flow managed by the Digia UI
     * DSL configuration.  On iOS this is a no-op until iOS support is added.
     */
    openNavigation(options: DigiaNavigationOptions = {}): void {
        nativeDigiaModule.openNavigation(
            options.startPageId ?? null,
            options.pageArgs ?? {}
        );
    }

    /**
     * Push a campaign payload into Digia's rendering engine.
     *
     * Called by JS-side CEP plugins (e.g. DigiaMoEngagePlugin from
     * @digia/moengage-react-native) when they receive a self-handled in-app
     * campaign from their own SDK.  Digia's overlay system (Compose
     * Dialog / BottomSheet) will render it according to the DSL configuration.
     *
     * @param id         Unique campaign ID from the CEP platform.
     * @param content    Marketer-authored payload (JSON-serialisable map).
     * @param cepContext CEP-platform metadata (e.g. { campaignId, campaignName }).
     */
    triggerCampaign(
        id: string,
        content: Record<string, unknown>,
        cepContext: Record<string, unknown>,
    ): void {
        nativeDigiaModule.triggerCampaign(id, content, cepContext);
    }

    /**
     * Dismiss / invalidate a currently-active campaign by its ID.
     */
    invalidateCampaign(campaignId: string): void {
        nativeDigiaModule.invalidateCampaign(campaignId);
    }
}

export const Digia = new DigiaClass();

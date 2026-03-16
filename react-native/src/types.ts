/**
 * Payload delivered to the Digia rendering engine for a CEP campaign.
 *
 * Mirrors InAppPayload on Android / Flutter.
 */
export interface InAppPayload {
    /** Unique campaign ID from the CEP platform. */
    id: string;
    /** Marketer-authored content map (JSON-serialisable). */
    content: Record<string, unknown>;
    /** CEP-platform metadata, e.g. { campaignId, campaignName }. */
    cepContext: Record<string, unknown>;
}

/**
 * Delegate passed by the Digia SDK to each registered plugin via setup().
 *
 * Mirrors DigiaCEPDelegate on Android / Flutter.
 * Call these instead of touching Digia directly from inside a plugin.
 */
export interface DigiaDelegate {
    /** Deliver a campaign payload into the Digia rendering engine. */
    onCampaignTriggered(payload: InAppPayload): void;
    /** Invalidate / dismiss a campaign by its ID. */
    onCampaignInvalidated(campaignId: string): void;
}

/**
 * Interface that every Digia CEP plugin must implement.
 *
 * Mirrors DigiaCEPPlugin on Android / Flutter.
 * Register plugins via `Digia.register(plugin)` — do NOT call setup() directly.
 */
export interface DigiaPlugin {
    readonly identifier: string;
    /** Called by Digia.register() — do not call manually. */
    setup(delegate: DigiaDelegate): void;
    /** Called by Digia.setCurrentScreen() — do not call manually. */
    forwardScreen(name: string): void;
    /** Called by Digia.unregister() or when tearing down the app. */
    teardown(): void;
}

/**
 * Configuration for initialising the Digia Engage SDK.
 */
export interface DigiaConfig {
    /** The API key provided by Digia. */
    apiKey: string;
    /**
     * The target environment.
     * @default 'production'
     */
    environment?: 'production' | 'sandbox';
    /**
     * Log verbosity.
     * @default 'error'
     */
    logLevel?: 'none' | 'error' | 'verbose';
}


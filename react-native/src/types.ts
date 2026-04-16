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

// ─── Experience events ────────────────────────────────────────────────────────

/** The experience became visible to the user. */
export interface ExperienceImpressed {
    readonly type: 'impressed';
}

/** The user interacted with an actionable element. */
export interface ExperienceClicked {
    readonly type: 'clicked';
    readonly elementId?: string;
}

/** The experience was dismissed — by the user or programmatically. */
export interface ExperienceDismissed {
    readonly type: 'dismissed';
}

/** Discriminated union of all experience event types. */
export type DigiaExperienceEvent =
    | ExperienceImpressed
    | ExperienceClicked
    | ExperienceDismissed;

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
    /**
     * Called by the Digia SDK when the overlay fires a lifecycle event
     * (impressed / clicked / dismissed). Plugins use this to report
     * analytics back to their CEP platform.
     */
    notifyEvent(event: DigiaExperienceEvent, payload: InAppPayload): void;
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


/**
 * The translation contract between a CEP plugin and Digia's rendering engine.
 *
 * Plugin authors map their CEP's native callback into this struct.
 * Mirrors CEPTriggerPayload on Android / Flutter — Digia core never imports
 * CleverTap, MoEngage, or WebEngage types directly.
 */
export interface CEPTriggerPayload {
    /** The CEP's own identifier for this campaign instance. Opaque to Digia — passed through for analytics correlation. */
    cepCampaignId: string;
    /** Additional metadata the CEP passes through (UTM params, user segment, CEP-specific tracking fields). Forwarded as-is in ExperienceEvents. */
    cepMetadata: Record<string, unknown>;
    /** The coupling key linking this CEP campaign to a Digia campaign. Used to look up the matching campaign in the store. */
    campaignKey: string;
    /** Optional runtime variables to interpolate into the campaign config. Keys must match variable placeholders in the Digia dashboard. */
    variables?: Record<string, string>;
}


export type CampaignType = 'nudge' | 'guide' | 'inline' | 'survey';

// ─── Experience events (CEP lifecycle — used by notifyEvent) ──────────────────

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

/** The user completed the experience (advanced past the final step). */
export interface ExperienceCompleted {
    readonly type: 'completed';
}

/** Discriminated union of all experience event types. */
export type DigiaExperienceEvent =
    | ExperienceImpressed
    | ExperienceClicked
    | ExperienceDismissed
    | ExperienceCompleted;

// ─── Guide lifecycle events (analytics — used by track()) ────────────────────

export type DismissReason = 'user_close' | 'scrim_tap' | 'back_gesture' | 'auto_timeout';

/**
 * Rich internal event emitted by guide overlays.
 * Carries all context needed to build the CEP analytics property schema.
 * Not part of the public plugin API — converted to track() calls in Digia.ts.
 */
export type GuideLifecycleEvent =
    | { type: 'viewed'; stepIndex: number; stepTotal: number; anchorKey: string; displayStyle: 'tooltip' | 'spotlight' }
    | { type: 'step_viewed'; stepIndex: number; stepTotal: number; anchorKey: string; displayStyle: 'tooltip' | 'spotlight' }
    | { type: 'clicked'; stepIndex: number; stepTotal: number; anchorKey: string; displayStyle: 'tooltip' | 'spotlight'; ctaLabel: string; actionType: string; actionUrl?: string; elementId?: string }
    | { type: 'step_clicked'; stepIndex: number; stepTotal: number; anchorKey: string; displayStyle: 'tooltip' | 'spotlight'; ctaLabel: string; actionType: string; actionUrl?: string; elementId?: string }
    | { type: 'dismissed'; stepIndex: number; stepTotal: number; anchorKey: string; displayStyle: 'tooltip' | 'spotlight'; dismissReason: DismissReason }
    | { type: 'step_dismissed'; stepIndex: number; stepTotal: number; anchorKey: string; displayStyle: 'tooltip' | 'spotlight'; dismissReason: DismissReason }
    | { type: 'completed'; stepIndex: number; stepTotal: number; anchorKey: string; displayStyle: 'tooltip' | 'spotlight' };

/**
 * Delegate passed by the Digia SDK to each registered plugin via setup().
 *
 * Mirrors DigiaCEPDelegate on Android / Flutter.
 * Call these instead of touching Digia directly from inside a plugin.
 */
export interface DigiaDelegate {
    /** Deliver a campaign payload into the Digia rendering engine. */
    onCampaignTriggered(payload: CEPTriggerPayload): void | Promise<void>;
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
    notifyEvent(event: DigiaExperienceEvent, payload: CEPTriggerPayload): void;
    /**
     * Called by the Digia SDK to record a named analytics event with properties.
     * Implement this to forward Digia lifecycle events (e.g. "Digia Experience Viewed")
     * to the CEP platform's custom event API.
     */
    track?(eventName: string, properties: Record<string, unknown>): void;
    /** Called by Digia.setCurrentScreen() — do not call manually. */
    forwardScreen(name: string): void;
    /** Called by Digia.unregister() or when tearing down the app. */
    teardown(): void;
}

// ─── Action types (public API) ────────────────────────────────────────────────

export type DigiaAction =
    | { type: 'deep_link'; url: string; fallback_url?: string }
    | { type: 'open_url'; url: string; presentation: 'external' | 'in_app' }
    | { type: 'dismiss'; scope?: 'self' | 'all' }
    | { type: 'next' }
    | { type: 'back' }
    | { type: 'fire_event'; event_name: string; properties?: Record<string, unknown> };

export type ActionContext = {
    campaign_id: string;
    campaign_key: string;
    campaign_type: 'nudge' | 'guide' | 'inline' | 'survey';
    source: {
        kind: 'button' | 'card_tap' | 'pip_small_view' | 'auto_dismiss';
        element_id?: string;
        button_label?: string;
    };
    step_index?: number;
    step_total?: number;
    source_node?: unknown;
};

export type ActionResult = boolean | Promise<boolean> | void;

export type OnAction = (action: DigiaAction, context: ActionContext) => ActionResult;

export type InAppBrowserAdapter = {
    open: (url: string) => Promise<void>;
};

// ─── Frequency capping ────────────────────────────────────────────────────────

export interface FrequencyWindow {
    count: number;
    window: 'session' | 'day' | 'week' | 'month';
}

export interface FrequencyPolicy {
    max_total: number | null;
    max_per_window: FrequencyWindow | null;
    stop_on: 'click' | 'dismiss' | 'any_action' | null;
    min_gap_ms?: number | null; // reserved — not evaluated in v1
}

export interface FrequencyState {
    shown_count: number;
    first_shown_at: number | null;      // ms timestamp — set on first impression
    last_shown_at: number | null;       // ms timestamp — reserved for min_gap_ms
    stopped_at: number | null;
    stopped_reason: string | null;
}

export type FrequencySkipReason = 'max_total' | 'window' | 'stopped';

export interface FrequencyEvalResult {
    allow: boolean;
    reason: FrequencySkipReason | null;
}

// ─── SDK init config ──────────────────────────────────────────────────────────

/**
 * Configuration for initialising the Digia Engage SDK.
 */
export interface DigiaConfig {
    /** The Engage project ID — sent as x-digia-project-id on all SDK requests. */
    projectId: string;
    /**
     * Base URL for the Digia API.
     * Defaults to the production API root, or the Engage sandbox root when
     * `environment` is "sandbox".
     */
    baseUrl?: string;
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
    /**
     * Optional global font family applied to all Digia-rendered text.
     * Must match a font available to the native app — an Android system/registered
     * family name, or an iOS bundled font's PostScript name.
     */
    fontFamily?: string;
    /**
     * Optional override hook called for every action before the SDK runs its
     * default behavior. Return true to suppress the default; false/void lets
     * the SDK handle it.
     */
    onAction?: OnAction;
    /**
     * URL / linking configuration.
     */
    linking?: {
        /**
         * When true (default) the SDK calls Linking.openURL for URL-bearing actions.
         * @default true
         */
        routeViaSystemLinking?: boolean;
        /**
         * Required if any campaign uses open_url with presentation: 'in_app'.
         * Falls back to Linking.openURL + emits inapp_browser_unavailable if absent.
         */
        inAppBrowser?: InAppBrowserAdapter;
    };
}

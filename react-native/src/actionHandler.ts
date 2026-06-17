import { AppState, type AppStateStatus, Linking } from 'react-native';
import type { DigiaAction, ActionContext, OnAction, InAppBrowserAdapter } from './types';
import type { Action } from './templateTypes';
import { digiaHealthReporter, HealthEventType } from './DigiaHealthReporter';

// ─── Types ────────────────────────────────────────────────────────────────────

export type ActionCallbacks = {
    onNext: () => void;
    onBack: () => void;
    onDismissSelf: () => void;
    onDismissAll: () => void;
};

type ActionHandlerConfig = {
    onAction?: OnAction;
    routeViaSystemLinking: boolean;
    inAppBrowser?: InAppBrowserAdapter;
    onFireEvent?: (eventName: string, properties?: Record<string, unknown>, context?: ActionContext) => void;
};

// ─── Internal state ───────────────────────────────────────────────────────────

let _config: ActionHandlerConfig = { routeViaSystemLinking: true };
let _lastActionKey = '';
let _lastActionAt = 0;
let _inappBrowserWarned = false;
const _invalidContextWarned = new Set<string>();
const MAX_WARNED_KEYS = 50;

const DEBOUNCE_MS = 500;
const HANDLER_TIMEOUT_MS = 2000;

// ─── Helpers ──────────────────────────────────────────────────────────────────

function isValidUrl(url: string): boolean {
    if (!url) return false;
    try {
        new URL(url);
        return true;
    } catch {
        return /^[a-zA-Z][a-zA-Z0-9+\-.]*:\/\//.test(url);
    }
}

function isDuplicate(action: DigiaAction, campaignId: string): boolean {
    const key = `${campaignId}:${action.type}`;
    const now = Date.now();
    if (key === _lastActionKey && now - _lastActionAt < DEBOUNCE_MS) return true;
    _lastActionKey = key;
    _lastActionAt = now;
    return false;
}

function emitHealth(eventType: HealthEventType, detail: Record<string, unknown>): void {
    digiaHealthReporter.report(eventType, detail);
}

// ─── Convert widget Action → public DigiaAction ───────────────────────────────

export const toDigiaAction = (action: Action): DigiaAction => {
    switch (action.type) {
        case 'dismiss':    return { type: 'dismiss', scope: action.scope };
        case 'next':       return { type: 'next' };
        case 'back':       return { type: 'back' };
        case 'prev':       return { type: 'back' };
        case 'deep_link':  return { type: 'deep_link', url: action.url, fallback_url: action.fallback_url };
        case 'open_url':   return { type: 'open_url', url: action.url, presentation: action.presentation };
        case 'fire_event': return { type: 'fire_event', event_name: action.event_name, properties: action.properties };
    }
};

// ─── onAction invocation with timeout + error handling ───────────────────────

async function callOnAction(
    onAction: OnAction,
    action: DigiaAction,
    context: ActionContext,
): Promise<boolean> {
    const timedOut = { value: false };
    try {
        const result = onAction(action, context);
        if (result === true) return true;
        if (result === false || result === undefined || result === null) return false;
        const timeoutPromise = new Promise<boolean>((resolve) =>
            setTimeout(() => { timedOut.value = true; resolve(false); }, HANDLER_TIMEOUT_MS),
        );
        const resolved = await Promise.race([
            Promise.resolve(result as Promise<boolean>).catch(() => false),
            timeoutPromise,
        ]);
        if (timedOut.value) {
            emitHealth(HealthEventType.action_handler_timeout, {
                campaign_id: context.campaign_id,
                action_type: action.type,
            });
        }
        return resolved === true;
    } catch (e) {
        const err = e instanceof Error ? e : new Error(String(e));
        emitHealth(HealthEventType.action_handler_threw, {
            campaign_id: context.campaign_id,
            action_type: action.type,
            error_message: err.message,
            error_stack: err.stack,
        });
        return false;
    }
}

// ─── Default behavior per action type ────────────────────────────────────────

async function runDefault(
    action: DigiaAction,
    context: ActionContext,
    callbacks: ActionCallbacks,
): Promise<void> {
    switch (action.type) {
        case 'deep_link': {
            if (!isValidUrl(action.url)) {
                emitHealth(HealthEventType.invalid_action_url, {
                    url: action.url, action_type: 'deep_link', campaign_id: context.campaign_id,
                });
                return;
            }
            const canOpen = await Linking.canOpenURL(action.url).catch(() => false);
            if (canOpen) {
                Linking.openURL(action.url).catch(() => {});
                callbacks.onDismissSelf();
                return;
            }
            if (action.fallback_url) {
                if (!isValidUrl(action.fallback_url)) {
                    emitHealth(HealthEventType.invalid_action_url, {
                        url: action.fallback_url, action_type: 'deep_link', campaign_id: context.campaign_id,
                    });
                } else {
                    const canOpenFallback = await Linking.canOpenURL(action.fallback_url).catch(() => false);
                    if (canOpenFallback) {
                        Linking.openURL(action.fallback_url).catch(() => {});
                        callbacks.onDismissSelf();
                        return;
                    }
                }
            }
            emitHealth(HealthEventType.deep_link_no_handler, {
                url: action.url,
                ...(action.fallback_url ? { fallback_url: action.fallback_url } : {}),
                campaign_id: context.campaign_id,
            });
            callbacks.onDismissSelf();
            return;
        }

        case 'open_url': {
            if (!isValidUrl(action.url)) {
                emitHealth(HealthEventType.invalid_action_url, {
                    url: action.url, action_type: 'open_url', campaign_id: context.campaign_id,
                });
                return;
            }
            const presentation = action.presentation ?? 'external';
            if (presentation === 'in_app' && _config.inAppBrowser) {
                _config.inAppBrowser.open(action.url).catch(() => {});
            } else {
                if (presentation === 'in_app' && !_inappBrowserWarned) {
                    _inappBrowserWarned = true;
                    emitHealth(HealthEventType.inapp_browser_unavailable, { campaign_id: context.campaign_id });
                }
                Linking.openURL(action.url).catch(() => {});
            }
            callbacks.onDismissSelf();
            return;
        }

        case 'dismiss': {
            if (action.scope === 'all') {
                callbacks.onDismissAll();
            } else {
                callbacks.onDismissSelf();
            }
            return;
        }

        case 'next': {
            const isLastStep =
                context.step_total !== undefined &&
                context.step_index !== undefined &&
                context.step_index >= context.step_total - 1;
            if (isLastStep) {
                const warnKey = `next:${context.campaign_id}`;
                if (!_invalidContextWarned.has(warnKey)) {
                    if (_invalidContextWarned.size >= MAX_WARNED_KEYS) _invalidContextWarned.clear();
                    _invalidContextWarned.add(warnKey);
                    emitHealth(HealthEventType.invalid_action_context, {
                        campaign_id: context.campaign_id, action_type: 'next',
                    });
                }
                callbacks.onDismissSelf();
                return;
            }
            callbacks.onNext();
            return;
        }

        case 'back': {
            if (context.step_index === 0) {
                const warnKey = `back:${context.campaign_id}`;
                if (!_invalidContextWarned.has(warnKey)) {
                    if (_invalidContextWarned.size >= MAX_WARNED_KEYS) _invalidContextWarned.clear();
                    _invalidContextWarned.add(warnKey);
                    emitHealth(HealthEventType.invalid_action_context, {
                        campaign_id: context.campaign_id, action_type: 'back',
                    });
                }
                return;
            }
            callbacks.onBack();
            return;
        }

        case 'fire_event': {
            _config.onFireEvent?.(action.event_name, action.properties, context);
            return;
        }
    }
}

// ─── Public interface ─────────────────────────────────────────────────────────

const configure = (config: Partial<ActionHandlerConfig>): void => {
    _config = { routeViaSystemLinking: true, ...config };
};

const execute = async (
    widgetAction: Action,
    context: ActionContext,
    callbacks: ActionCallbacks,
): Promise<void> => {
    const action = toDigiaAction(widgetAction);

    if (isDuplicate(action, context.campaign_id)) return;

    // Guard: queue if app not in foreground (cold-start scenario)
    if (AppState.currentState !== 'active') {
        digiaActionQueue.enqueue(widgetAction, action.type, context, callbacks);
        return;
    }

    // 1. Analytics click — fire-and-forget (handled by caller via onExperienceEvent)

    // 2. Invoke onAction override
    let handled = false;
    if (_config.onAction) {
        handled = await callOnAction(_config.onAction, action, context);
    }

    // 3. Run default if not handled
    if (!handled) {
        await runDefault(action, context, callbacks);
        return;
    }
    if (action.type === 'deep_link' || action.type === 'open_url') {
        callbacks.onDismissSelf();
    }
};

export const digiaActionHandler = { configure, execute };

// ─── Cold-start queue ─────────────────────────────────────────────────────────

type QueuedItem = {
    widgetAction: Action;
    actionType: string;
    context: ActionContext;
    callbacks: ActionCallbacks;
};

const MAX_QUEUE_SIZE = 5;

class ActionQueue {
    private _items: QueuedItem[] = [];
    private _subscription: { remove(): void } | null = null;

    constructor() {
        this._subscription = AppState.addEventListener('change', (state: AppStateStatus) => {
            if (state === 'active') this._flush();
        });
    }

    enqueue(widgetAction: Action, actionType: string, context: ActionContext, callbacks: ActionCallbacks): void {
        if (this._items.length >= MAX_QUEUE_SIZE) {
            const dropped = this._items.shift()!;
            emitHealth(HealthEventType.cold_start_queue_overflow, {
                dropped_action_type: dropped.actionType,
                dropped_campaign_id: dropped.context.campaign_id,
            });
        }
        this._items.push({ widgetAction, actionType, context, callbacks });
    }

    destroy(): void {
        this._subscription?.remove();
        this._subscription = null;
        this._items = [];
    }

    private _flush(): void {
        const items = this._items.splice(0);
        const processAt = (index: number) => {
            if (index >= items.length) return;
            const item = items[index];
            digiaActionHandler.execute(item.widgetAction, item.context, item.callbacks).finally(() => {
                setTimeout(() => processAt(index + 1), 100);
            });
        };
        processAt(0);
    }
}

const digiaActionQueue = new ActionQueue();

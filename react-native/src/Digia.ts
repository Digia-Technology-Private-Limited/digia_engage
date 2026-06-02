/**
 * High-level Digia Engage SDK wrapper.
 *
 * Usage
 * ──────
 * ```ts
 * import { Digia } from '@digia/engage-react-native';
 *
 * // In your App entry point (e.g. App.tsx):
 * await Digia.initialize({ projectId: 'YOUR_PROJECT_ID' });
 *
 * // Whenever your navigation screen changes:
 * Digia.setCurrentScreen('Home');
 *
 * ```
 */

import { DeviceEventEmitter } from 'react-native';
import { nativeDigiaModule } from './NativeDigiaEngage';
import { digiaHealthReporter, HealthEventType } from './DigiaHealthReporter';
import { digiaGuideController } from './DigiaGuideController';
import { parseVariableMap } from './interpolate';
import { digiaActionHandler } from './actionHandler';
import uuid from 'react-native-uuid';
import { frequencyStore } from './frequencyStore';
import { evaluate, hasPolicy, isSessionPolicy } from './frequencyEvaluator';
import type {
    ActionContext,
    CampaignType,
    DigiaConfig,
    DigiaDelegate,
    DigiaExperienceEvent,
    DigiaPlugin,
    FrequencyPolicy,
    FrequencyState,
    GuideLifecycleEvent,
    InAppPayload,
} from './types';
import type { TemplateConfig } from './templateTypes';

const PRODUCTION_API_ROOT = 'https://app.digia.tech';
const SANDBOX_API_ROOT = 'https://dev.digia.tech';
const DIGIA_SDK_VERSION = '1.0.0';

interface SdkCampaign {
    id?: string;
    _id?: string;
    campaign_key: string;
    campaign_type: CampaignType;
    templateConfig?: Record<string, unknown>;
    frequency?: FrequencyPolicy | null;
}

class DigiaClass implements DigiaDelegate {
    private readonly _plugins = new Map<string, DigiaPlugin>();
    // Tracks whether the native bridge plugin (RNEventBridgePlugin) has been
    // wired to the native SDK. Done once on the first Digia.register() call.
    private _nativeBridgeWired = false;
    // Cache of triggered payloads keyed by campaign ID, used to reconstruct
    // the full InAppPayload when overlay lifecycle events arrive from native.
    private readonly _activePayloads = new Map<string, InAppPayload>();
    private _engageSubscription: { remove(): void } | null = null;
    private _projectId = '';
    private _deviceId = '';
    private _apiBaseUrl = '';
    private _logLevel: DigiaConfig['logLevel'] = 'error';
    private _fontFamily: string | undefined;
    private _currentScreen: string | null = null;
    private readonly _campaignsByKey = new Map<string, SdkCampaign>();
    private readonly _registeredAnchorKeys = new Set<string>();

    /**
     * Initialise the Digia Engage SDK.
     *
     * Must be called once before anything else – ideally as early as possible
     * in your application lifecycle (start of `App.tsx`).
     */
    async initialize(config: DigiaConfig): Promise<void> {
        const environment = config.environment ?? 'production';
        const logLevel = config.logLevel ?? 'error';
        this._projectId = config.projectId;
        this._apiBaseUrl = this._resolveApiBaseUrl(config);
        this._logLevel = logLevel;
        this._fontFamily = config.fontFamily?.trim() || undefined;
        digiaHealthReporter.init(config.projectId, this._apiBaseUrl);

        digiaActionHandler.configure({
            onAction: config.onAction,
            routeViaSystemLinking: config.linking?.routeViaSystemLinking ?? true,
            inAppBrowser: config.linking?.inAppBrowser,
            onFireEvent: (eventName, properties, context) =>
                this._fireCustomEvent(eventName, properties, context),
        });

        try {
            await nativeDigiaModule.initialize(config.projectId, environment, logLevel, config.baseUrl, config.fontFamily);
        } catch (e) {
            // TODO: TO BE PICKED LATER @aditya-digia — health event reporting being removed
            // digiaHealthReporter.report(HealthEventType.fetch_failed, { error_code: 0, platform: 'react_native' });
            throw e;
        }

        this._deviceId = await this._loadOrCreateDeviceId();
        await frequencyStore.checkProjectId(config.projectId);
        await this._refreshCampaignStore();
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
     * await Digia.initialize({ projectId: 'YOUR_PROJECT_ID' });
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
        (plugin as any).reportHealth?.(digiaHealthReporter);
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
        this._currentScreen = name;
        nativeDigiaModule.setCurrentScreen(name);
        this._plugins.forEach((plugin) => plugin.forwardScreen(name));
    }

    registerAnchor(anchorKey: string, _screenName?: string | null): void {
        const cleanAnchorKey = anchorKey.trim();
        if (!cleanAnchorKey) return;
        this._registeredAnchorKeys.add(cleanAnchorKey);
    }

    unregisterAnchor(anchorKey: string): void {
        this._registeredAnchorKeys.delete(anchorKey);
    }

    /**
     * Global font family configured via {@link initialize}, or `undefined` when
     * none was set. Used by the JS-rendered guide overlays (tooltip/spotlight)
     * so their text matches native-rendered campaigns.
     */
    get fontFamily(): string | undefined {
        return this._fontFamily;
    }


    // ── DigiaDelegate ────────────────────────────────────────────────────────
    // Mirrors DigiaCEPDelegate on Android.
    // Forwards to the native DigiaCEPDelegate via the bridge.

    async onCampaignTriggered(payload: InAppPayload): Promise<void> {
        if (!this._nativeBridgeWired) {
            digiaHealthReporter.report(HealthEventType.plugin_not_registered, { campaign_key: payload.id });
        }

        const campaignKey = this._extractCampaignKey(payload);
        this._log(`onCampaignTriggered payloadId=${payload.id} extractedKey=${campaignKey} knownKeys=[${[...this._campaignsByKey.keys()].join(', ')}]`);

        if (campaignKey) {
            const campaign = this._campaignsByKey.get(campaignKey);

            if (campaign && hasPolicy(campaign.frequency)) {
                const policy = campaign.frequency!;
                const isSession = isSessionPolicy(policy);
                const state = await this._getFrequencyState(campaignKey, isSession);
                const result = evaluate(policy, state, Date.now());
                if (!result.allow) {
                    this._log(`frequency_capped campaign_key=${campaignKey} reason=${result.reason}`);
                    return;
                }
            }

            if (campaign?.campaign_type === 'inline' || campaign?.campaign_type === 'survey') {
                this._log(`${campaign.campaign_type} campaign triggered campaign_key=${campaignKey}, forwarding to native`);
                this._activePayloads.set(payload.id, payload);
                if (campaign.campaign_type === 'inline') {
                    this._emitSlotWidth(campaign);
                }
                nativeDigiaModule.triggerCampaign(payload.id, payload.content, payload.cepContext);
                return;
            }

            if (campaign?.campaign_type === 'guide') {
                const config = this._parseTemplateConfig(campaign);
                if (
                    !config ||
                    (config.templateType !== 'tooltip' && config.templateType !== 'spotlight') ||
                    config.steps.length === 0
                ) {
                    digiaHealthReporter.report(HealthEventType.anchor_not_on_screen, {
                        campaign_key: campaignKey,
                        reason: 'guide_campaign_has_no_steps',
                    });
                    return;
                }

                this._activePayloads.set(payload.id, payload);
                const digiaId = campaign._id ?? campaign.id ?? campaignKey;
                const mounted = digiaGuideController.start({
                    payloadId: payload.id,
                    campaignKey,
                    campaignId: digiaId,
                    variables: this._extractVariables(payload),
                    config,
                    onExperienceEvent: (event) => this._onGuideLifecycleEvent(event, payload.id, campaignKey, digiaId),
                });

                this._log(`guide trigger campaign_key=${campaignKey} mounted=${mounted}`);
                if (!mounted) {
                    digiaHealthReporter.report(HealthEventType.host_not_mounted, {
                        campaign_key: campaignKey,
                        payload_id: payload.id,
                    });
                }
                return;
            }

            if (!campaign) {
                this._log(`campaign_key_mismatch: no campaign found for key="${campaignKey}"`);
                digiaHealthReporter.report(HealthEventType.campaign_key_mismatch, {
                    campaign_key: campaignKey,
                    payload_id: payload.id,
                    available_campaign_keys: [...this._campaignsByKey.keys()],
                });
                return;
            }
        }

        this._activePayloads.set(payload.id, payload);
        nativeDigiaModule.triggerCampaign(payload.id, payload.content, payload.cepContext);
    }

    onCampaignInvalidated(campaignId: string): void {
        this._activePayloads.delete(campaignId);
        digiaGuideController.cancel(campaignId);
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

    private _fireCustomEvent(
        eventName: string,
        properties?: Record<string, unknown>,
        context?: ActionContext,
    ): void {
        const payload = context ? this._activePayloads.get(context.campaign_id) : null;
        if (payload) {
            const event: DigiaExperienceEvent = { type: 'clicked', elementId: eventName };
            this._plugins.forEach((plugin) => plugin.notifyEvent(event, payload));
        }
        // TODO: record custom event to Digia analytics endpoint when available
    }

    private _forwardExperienceEvent(
        data: { campaignId: string; type: string; elementId?: string },
    ): void {
        const payload = this._activePayloads.get(data.campaignId);
        if (!payload) return;

        const campaignKey = this._extractCampaignKey(payload);

        let event: DigiaExperienceEvent;
        switch (data.type) {
            case 'impressed':
                event = { type: 'impressed' };
                if (campaignKey) void this._bumpFrequencyImpression(campaignKey);
                break;
            case 'clicked':
                event = { type: 'clicked', elementId: data.elementId };
                if (campaignKey) void this._applyStopOn(campaignKey, 'click');
                break;
            case 'dismissed':
                event = { type: 'dismissed' };
                if (campaignKey) void this._applyStopOn(campaignKey, 'dismiss');
                this._activePayloads.delete(data.campaignId);
                break;
            default:
                return;
        }

        this._plugins.forEach((plugin) => plugin.notifyEvent(event, payload));
    }

    private _onGuideLifecycleEvent(
        event: GuideLifecycleEvent,
        payloadId: string,
        campaignKey: string,
        campaignId: string,
    ): void {
        const eventName = this._guideEventName(event.type);
        const properties = this._buildGuideProperties(event, campaignId, campaignKey);
        this._plugins.forEach((p) => p.track?.(eventName, properties));

        if (event.type === 'viewed') {
            void this._bumpFrequencyImpression(campaignKey);
        }
        if (event.type === 'clicked' || event.type === 'completed') {
            void this._applyStopOn(campaignKey, 'click');
        }
        if (event.type === 'dismissed') {
            void this._applyStopOn(campaignKey, 'dismiss');
        }

        // Notify plugins of CEP lifecycle termination (template cleanup) on exit events.
        if (event.type === 'dismissed' || event.type === 'completed') {
            const storedPayload = this._activePayloads.get(payloadId);
            if (storedPayload) {
                this._plugins.forEach((p) => p.notifyEvent({ type: 'dismissed' }, storedPayload));
                this._activePayloads.delete(payloadId);
            }
        }
    }

    private _guideEventName(type: GuideLifecycleEvent['type']): string {
        switch (type) {
            case 'viewed': return 'Digia Experience Viewed';
            case 'step_viewed': return 'Digia Step Viewed';
            case 'clicked': return 'Digia Experience Clicked';
            case 'step_clicked': return 'Digia Step Clicked';
            case 'dismissed': return 'Digia Experience Dismissed';
            case 'step_dismissed': return 'Digia Step Dismissed';
            case 'completed': return 'Digia Experience Completed';
        }
    }

    private _buildGuideProperties(
        event: GuideLifecycleEvent,
        campaignId: string,
        campaignKey: string,
    ): Record<string, unknown> {
        const base: Record<string, unknown> = {
            campaign_id: campaignId,
            campaign_key: campaignKey,
            campaign_type: 'guide',
            display_style: event.displayStyle,
            step_index: event.stepIndex + 1,
            step_total: event.stepTotal,
            anchor_key: event.anchorKey,
            slot_key: null,
            element_id: null,
            cta_label: null,
            action_type: null,
            action_url: null,
            dismiss_reason: null,
            abandoned_at_step: null,
            digia_sdk_version: DIGIA_SDK_VERSION,
            digia_platform: 'react_native',
        };

        if (event.type === 'clicked' || event.type === 'step_clicked') {
            base.element_id = event.elementId ?? null;
            base.cta_label = event.ctaLabel;
            base.action_type = event.actionType;
            base.action_url = event.actionUrl ?? null;
        } else if (event.type === 'dismissed' || event.type === 'step_dismissed') {
            base.dismiss_reason = event.dismissReason;
            base.abandoned_at_step = event.stepIndex + 1;
        }

        return base;
    }

    private _resolveApiBaseUrl(config: DigiaConfig): string {
        const root = (config.baseUrl ??
            (config.environment === 'sandbox' ? SANDBOX_API_ROOT : PRODUCTION_API_ROOT)).trim();
        const cleanRoot = root.replace(/\/+$/, '');
        return cleanRoot.endsWith('/api/v1') ? cleanRoot : `${cleanRoot}/api/v1`;
    }

    private async _refreshCampaignStore(): Promise<void> {
        try {
            this._log(`fetching campaigns from ${this._apiBaseUrl}/engage/sdk/getCampaigns`);
            const campaigns = await this._sdkPost<SdkCampaign[]>('getCampaigns');
            this._campaignsByKey.clear();
            if (campaigns.length > 0) {
                this._log(`campaign[0] raw keys: ${JSON.stringify(Object.keys(campaigns[0] as object))}`);
            }
            campaigns.forEach((campaign) => {
                const raw = campaign as unknown as Record<string, unknown>;
                const key = (typeof raw.campaign_key === 'string' && raw.campaign_key)
                    || (typeof raw.campaignKey === 'string' && raw.campaignKey)
                    || null;
                const type = (typeof raw.campaign_type === 'string' && raw.campaign_type)
                    || (typeof raw.campaignType === 'string' && raw.campaignType)
                    || '';
                if (key) {
                    this._campaignsByKey.set(key, { ...campaign, campaign_key: key, campaign_type: type as SdkCampaign['campaign_type'] });
                }
            });
            this._log(`loaded ${campaigns.length} campaign(s): [${[...this._campaignsByKey.keys()].join(', ')}]`);
        } catch (e) {
            const reason = e instanceof Error ? e.message : String(e);
            this._log(`getCampaigns FAILED: ${reason}`);
            digiaHealthReporter.report(HealthEventType.fetch_failed, {
                error_code: 0,
                platform: 'react_native',
                reason,
            });
        }
    }

    private async _sdkPost<T>(path: string, body: Record<string, unknown> = {}): Promise<T> {
        const res = await fetch(`${this._apiBaseUrl}/engage/sdk/${path}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'x-digia-project-id': this._projectId,
                'x-digia-device-id': this._deviceId,
            },
            body: JSON.stringify(body),
        });

        if (!res.ok) {
            throw new Error(`${path} failed: HTTP ${res.status}`);
        }

        const json = await res.json();
        return this._extractApiResponse<T>(json);
    }

    private _extractApiResponse<T>(json: unknown): T {
        if (Array.isArray(json)) return json as T;
        if (json && typeof json === 'object') {
            const obj = json as Record<string, unknown>;
            const data = obj.data;
            if (data && typeof data === 'object' && 'response' in data) {
                const value = (data as Record<string, unknown>).response;
                if (value == null) throw new Error('SDK response.data.response is null');
                return value as T;
            }
            if ('response' in obj) {
                const value = obj.response;
                if (value == null) throw new Error('SDK response.response is null');
                return value as T;
            }
        }
        throw new Error('SDK response missing data.response');
    }

    private _emitSlotWidth(campaign: SdkCampaign): void {
        const raw = campaign as unknown as Record<string, unknown>;
        const config = raw.templateConfig as Record<string, unknown> | undefined;
        if (!config) {
            this._log(`_emitSlotWidth: no templateConfig for campaign ${campaign.campaign_key}`);
            return;
        }
        const slotKey = typeof config.slotKey === 'string' ? config.slotKey : null;
        const width = typeof config.width === 'number' && config.width > 0 ? config.width : null;
        this._log(`_emitSlotWidth slotKey=${slotKey} width=${width} campaign=${campaign.campaign_key}`);
        if (slotKey) {
            DeviceEventEmitter.emit('digiaSlotWidth', { slotKey, width });
        } else {
            this._log(`_emitSlotWidth: no slotKey in templateConfig ${JSON.stringify(config)}`);
        }
    }

    private _extractCampaignKey(payload: InAppPayload): string | null {
        const fromContent = this._extractString(payload.content, 'digia_campaign_key', 'digiaKey', 'campaign_key', 'campaignKey');
        if (fromContent) return fromContent;

        const args = payload.content.args;
        if (args && typeof args === 'object' && !Array.isArray(args)) {
            const fromArgs = this._extractString(args as Record<string, unknown>, 'digia_campaign_key', 'digiaKey', 'campaign_key', 'campaignKey');
            if (fromArgs) return fromArgs;
        }

        if (this._campaignsByKey.has(payload.id)) return payload.id;
        return null;
    }

    private _extractVariables(payload: InAppPayload): Record<string, string> | undefined {
        const fromContent = parseVariableMap(payload.content.variables);
        if (fromContent) return fromContent;

        const args = payload.content.args;
        if (args && typeof args === 'object' && !Array.isArray(args)) {
            return parseVariableMap((args as Record<string, unknown>).variables);
        }
        return undefined;
    }

    private _extractString(data: Record<string, unknown>, ...keys: string[]): string | null {
        for (const key of keys) {
            const value = data[key];
            if (typeof value === 'string' && value.trim()) return value.trim();
        }
        return null;
    }

    private _parseTemplateConfig(campaign: SdkCampaign): TemplateConfig | null {
        const c = campaign as unknown as Record<string, unknown>;
        const raw = c.templateConfig as Record<string, unknown> | undefined;
        if (!raw || typeof raw !== 'object') return null;
        const type = raw.templateType;
        if (type !== 'tooltip' && type !== 'spotlight' && type !== 'carousel') {
            this._log(`unknown templateType="${type}" for campaign_key=${campaign.campaign_key}`);
            return null;
        }
        if (type === 'carousel') {
            return raw as TemplateConfig;
        }
        const steps = Array.isArray(raw.steps) ? raw.steps : [];
        return { ...raw, templateType: type, steps } as TemplateConfig;
    }

    // ── Device ID ────────────────────────────────────────────────────────────

    private async _loadOrCreateDeviceId(): Promise<string> {
        const DEVICE_ID_KEY = 'digia:device_id';
        try {
            // eslint-disable-next-line @typescript-eslint/no-var-requires
            const AsyncStorage = require('@react-native-async-storage/async-storage').default;
            const stored = await AsyncStorage.getItem(DEVICE_ID_KEY);
            if (stored) return stored;
            const id = uuid.v4() as string;
            await AsyncStorage.setItem(DEVICE_ID_KEY, id);
            return id;
        } catch {
            return uuid.v4() as string;
        }
    }

    // ── Frequency capping ────────────────────────────────────────────────────

    private async _getFrequencyState(campaignKey: string, isSession: boolean): Promise<FrequencyState | null> {
        return frequencyStore.get(campaignKey, isSession);
    }

    async _bumpFrequencyImpression(campaignKey: string): Promise<void> {
        const campaign = this._campaignsByKey.get(campaignKey);
        if (!campaign || !hasPolicy(campaign.frequency)) return;
        const isSession = isSessionPolicy(campaign.frequency!);
        const now = Date.now();
        const prev = await frequencyStore.get(campaignKey, isSession);
        const next: FrequencyState = {
            shown_count: (prev?.shown_count ?? 0) + 1,
            first_shown_at: prev?.first_shown_at ?? now,
            last_shown_at: now,
            stopped_at: prev?.stopped_at ?? null,
            stopped_reason: prev?.stopped_reason ?? null,
        };
        await frequencyStore.set(campaignKey, next, isSession);
    }

    async _applyStopOn(campaignKey: string, interactionType: 'click' | 'dismiss'): Promise<void> {
        const campaign = this._campaignsByKey.get(campaignKey);
        const stopOn = campaign?.frequency?.stop_on;
        if (!stopOn) return;
        const matches =
            stopOn === 'any_action' ||
            stopOn === interactionType;
        if (!matches) return;
        const isSession = isSessionPolicy(campaign!.frequency!);
        const prev = await frequencyStore.get(campaignKey, isSession);
        if (prev?.stopped_at) return;
        const now = Date.now();
        const next: FrequencyState = {
            shown_count: prev?.shown_count ?? 0,
            first_shown_at: prev?.first_shown_at ?? null,
            last_shown_at: prev?.last_shown_at ?? null,
            stopped_at: now,
            stopped_reason: interactionType,
        };
        await frequencyStore.set(campaignKey, next, isSession);
    }

    private _log(message: string): void {
        if (this._logLevel !== 'verbose') return;
        // eslint-disable-next-line no-console
        console.log(`[Digia] ${message}`);
    }

}

export const Digia = new DigiaClass();

export const HealthEventType = {
  campaign_key_mismatch: 'campaign_key_mismatch',
  component_orphaned: 'component_orphaned',
  anchor_not_on_screen: 'anchor_not_on_screen',
  host_not_mounted: 'host_not_mounted',
  plugin_not_registered: 'plugin_not_registered',
  fetch_failed: 'fetch_failed',
} as const;

export type HealthEventType = (typeof HealthEventType)[keyof typeof HealthEventType];

export class DigiaHealthReporter {
    private _apiKey = '';
    private _baseUrl = '';

    init(apiKey: string, baseUrl: string): void {
        this._apiKey = apiKey;
        this._baseUrl = baseUrl;
    }

    report(eventType: HealthEventType, detail: Record<string, unknown>): void {
        if (!this._apiKey) return;
        try {
            fetch(`${this._baseUrl}/engage/sdk/recordHealthEvent`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'x-api-key': this._apiKey },
                body: JSON.stringify({ event_type: eventType, detail }),
            }).catch(() => { /* swallow */ });
        } catch {
            // never throw
        }
    }
}

export const digiaHealthReporter = new DigiaHealthReporter();

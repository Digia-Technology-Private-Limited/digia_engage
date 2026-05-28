export const HealthEventType = {
  campaign_key_mismatch: 'campaign_key_mismatch',
  component_orphaned: 'component_orphaned',
  anchor_not_on_screen: 'anchor_not_on_screen',
  host_not_mounted: 'host_not_mounted',
  plugin_not_registered: 'plugin_not_registered',
  fetch_failed: 'fetch_failed',
  action_handler_threw: 'action_handler_threw',
  action_handler_timeout: 'action_handler_timeout',
  deep_link_no_handler: 'deep_link_no_handler',
  invalid_action_url: 'invalid_action_url',
  inapp_browser_unavailable: 'inapp_browser_unavailable',
  invalid_action_context: 'invalid_action_context',
  cold_start_queue_overflow: 'cold_start_queue_overflow',
} as const;

export type HealthEventType = (typeof HealthEventType)[keyof typeof HealthEventType];

export class DigiaHealthReporter {
    private _projectId = '';
    private _baseUrl = '';

    init(projectId: string, baseUrl: string): void {
        this._projectId = projectId;
        this._baseUrl = baseUrl;
    }

    report(eventType: HealthEventType, detail: Record<string, unknown>): void {
        if (!this._projectId) return;
        // TODO: TO BE PICKED LATER @aditya-digia — health event backend endpoint being removed
        // fetch(`${this._baseUrl}/engage/sdk/recordHealthEvent`, {
        //     method: 'POST',
        //     headers: {
        //         'Content-Type': 'application/json',
        //         'x-digia-project-id': this._projectId,
        //     },
        //     body: JSON.stringify({ event_type: eventType, detail }),
        // }).catch(() => { /* swallow */ });
        if (__DEV__) console.log('[DigiaHealth]', eventType, detail);
    }
}

export const digiaHealthReporter = new DigiaHealthReporter();

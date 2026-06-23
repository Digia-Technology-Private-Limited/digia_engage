/**
 * RNEventBridgePlugin
 *
 * Implements DigiaCEPPlugin so it can be registered with the native iOS Digia
 * SDK via Digia.register(). It acts as the bridge between the native SDK's
 * DigiaCEPDelegate and the JS layer.
 *
 * Lifecycle
 * ─────────
 * 1. JS calls Digia.register() → native registerBridge() → this plugin is
 *    registered with the native SDK via Digia.register(rnPlugin).
 * 2. The SDK calls setup(delegate:) storing the delegate reference.
 * 3. When a JS CEP plugin delivers a campaign via onCampaignTriggered(),
 *    the JS side calls triggerCampaign() which forwards here via DigiaModule.
 * 4. Overlay lifecycle events (impressed / clicked / dismissed) travel back to
 *    JS as DeviceEventEmitter events.
 */
import Foundation
import React
import DigiaEngage

@objc
internal final class RNEventBridgePlugin: NSObject, DigiaCEPPlugin {

    let identifier = "com.digia.rn.bridge"

    /// Populated by the SDK when registered. Used to forward JS-side campaigns
    /// into the native overlay rendering pipeline.
    private(set) weak var delegate: DigiaCEPDelegate?

    private weak var eventEmitter: RCTEventEmitter?

    init(eventEmitter: RCTEventEmitter?) {
        self.eventEmitter = eventEmitter
    }

    // MARK: - DigiaCEPPlugin

    func setup(delegate: DigiaCEPDelegate) {
        self.delegate = delegate
    }

    func notifyEvent(_ event: DigiaExperienceEvent, payload: CEPTriggerPayload) {

        var body: [String: Any] = ["campaignId": payload.cepCampaignId]
        switch event {
        case .impressed:
            body["type"] = "impressed"
        case .clicked(let elementID):
            body["type"] = "clicked"
            if let elementID {
                body["elementId"] = elementID
            }
        case .dismissed:
            body["type"] = "dismissed"
        @unknown default:
            // Unknown future event kind — nothing meaningful to forward.
            return
        }
        let hasEmitter = eventEmitter != nil
        print("[DigiaRN] RNEventBridgePlugin.notifyEvent type=\(body["type"] ?? "?") campaignId=\(payload.cepCampaignId) hasEmitter=\(hasEmitter)")
        eventEmitter?.sendEvent(withName: "digiaEngageEvent", body: body)
    }

    /// Forwards the action to the JS layer (the host handles routing), so it
    /// reports the action as handled — the native open fallback is skipped.
    func notifyAction(actionType: String, url: String, payload: CEPTriggerPayload) -> Bool {
        let body: [String: Any] = [
            "campaignId": payload.cepCampaignId,
            "type": "action",
            "actionType": actionType,
            "url": url,
        ]
        eventEmitter?.sendEvent(withName: "digiaEngageEvent", body: body)
        return true
    }

    func healthCheck() -> DiagnosticReport {
        return DiagnosticReport(
            isHealthy: delegate != nil,
            issue: delegate == nil ? "No delegate set" : nil,
            resolution: delegate == nil ? "Call registerBridge() before triggerCampaign()" : nil
        )
    }

    func teardown() {
        delegate = nil
    }
}

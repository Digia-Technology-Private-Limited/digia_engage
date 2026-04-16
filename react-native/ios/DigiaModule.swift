/**
 * DigiaModule
 *
 * React Native NativeModule that bridges the Digia Engage iOS SDK.
 *
 * Exposed methods (callable from JS via NativeModules.DigiaEngageModule):
 *   initialize(apiKey, environment, logLevel): Promise<void>
 *   registerBridge(): void
 *   setCurrentScreen(name): void
 *   triggerCampaign(id, content, cepContext): void
 *   invalidateCampaign(campaignId): void
 *
 * Architecture
 * ────────────
 * The RN bridge mirrors the native Digia.initialize / Digia.register /
 * Digia.setCurrentScreen flow exactly. An internal RNEventBridgePlugin is
 * the single native DigiaCEPPlugin registered via Digia.register().
 *
 * When the SDK calls plugin.setup(delegate:), the bridge stores that
 * delegate reference. JS plugins that deliver campaigns call triggerCampaign /
 * invalidateCampaign which forward to delegate.onCampaignTriggered /
 * delegate.onCampaignInvalidated.
 *
 * Overlay lifecycle events (impressed / clicked / dismissed) are forwarded from
 * the native plugin.notifyEvent() to JS via RCTEventEmitter so that pure-JS
 * CEP plugins (e.g. DigiaMoEngagePlugin) can report analytics.
 */
import Foundation
import React
import SwiftUI
import UIKit
import DigiaEngage

@objc(DigiaEngageModule)
final class DigiaModule: RCTEventEmitter {

    private lazy var rnPlugin: RNEventBridgePlugin = MainActor.assumeIsolated {
        RNEventBridgePlugin(eventEmitter: self)
    }

    // ────────────────────────────────────────────────────────────────────────
    // MARK: - RCTEventEmitter

    override static func requiresMainQueueSetup() -> Bool { true }

    override init() {
        super.init()
        // Pre-seed _listenerCount = 1 so sendEventWithName: never silently drops
        // events when JS uses DeviceEventEmitter (which doesn't call native
        // addListener: on iOS and therefore never increments the count).
        addListener("digiaEngageEvent")
    }

    override func supportedEvents() -> [String]! {
        return ["digiaEngageEvent"]
    }

    // ────────────────────────────────────────────────────────────────────────
    // MARK: - initialize

    @objc
    func initialize(
        _ apiKey: String,
        environment: String,
        logLevel: String,
        resolve: @escaping RCTPromiseResolveBlock,
        reject: @escaping RCTPromiseRejectBlock
    ) {
        let envValue: DigiaEnvironment = environment.lowercased() == "sandbox" ? .sandbox : .production
        let logLevelValue: DigiaLogLevel
        switch logLevel.lowercased() {
        case "verbose": logLevelValue = .verbose
        case "none":    logLevelValue = .none
        default:        logLevelValue = .error
        }

        let config = DigiaConfig(
            apiKey: apiKey,
            logLevel: logLevelValue,
            environment: envValue
        )

        Task { @MainActor in
            do {
                try await Digia.initialize(config)
                self.mountDigiaHost()
                resolve(nil)
            } catch {
                reject("DIGIA_INIT_ERROR", error.localizedDescription, error)
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // MARK: - registerBridge

    /// Registers RNEventBridgePlugin with the native Digia SDK.
    /// Called automatically by the JS Digia.register() wrapper on first plugin
    /// registration so that the delegate is populated before any
    /// triggerCampaign / invalidateCampaign calls arrive from JS.
    @objc
    func registerBridge() {
        Task { @MainActor in
            Digia.register(self.rnPlugin)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // MARK: - setCurrentScreen

    @objc
    func setCurrentScreen(_ name: String) {
        // Digia.setCurrentScreen is not yet part of the public iOS API; this is
        // a no-op placeholder that can be activated once it is added.
        // Digia.setCurrentScreen(name)
    }

    // ────────────────────────────────────────────────────────────────────────
    // MARK: - triggerCampaign

    /// Forwards a campaign payload to the native DigiaCEPDelegate.
    ///
    /// Called by the JS DigiaDelegate.onCampaignTriggered() when a JS CEP
    /// plugin (e.g. DigiaMoEngagePlugin) delivers a campaign. The delegate
    /// routes it into the SwiftUI overlay for rendering.
    @objc
    func triggerCampaign(
        _ id: String,
        content contentMap: NSDictionary,
        cepContext cepContextMap: NSDictionary
    ) {
        let content = buildInAppPayloadContent(from: contentMap)
        let cepContext = (cepContextMap as? [String: String]) ?? [:]
        let payload = InAppPayload(id: id, content: content, cepContext: cepContext)

        Task { @MainActor in
            guard let delegate = self.rnPlugin.delegate else { return }
            delegate.onCampaignTriggered(payload)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // MARK: - invalidateCampaign

    @objc
    func invalidateCampaign(_ campaignId: String) {
        Task { @MainActor in
            guard let delegate = self.rnPlugin.delegate else { return }
            delegate.onCampaignInvalidated(campaignId)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // MARK: - Internal: mount the SwiftUI overlay host

    /// Mirrors Android's DigiaModule.mountDigiaHost().
    /// Called once after Digia.initialize() succeeds — no need for a manual
    /// <DigiaHostView> anywhere in the JS component tree.
    @MainActor
    private func mountDigiaHost() {
        // Locate the key window's root view controller.
        guard let rootVC = UIApplication.shared
            .connectedScenes
            .compactMap({ ($0 as? UIWindowScene)?.keyWindow })
            .first?
            .rootViewController else { return }

        // Guard against double-mounting (e.g. fast-refresh).
        let mountTag = 0xD19140
        if rootVC.view.viewWithTag(mountTag) != nil { return }

        let hc = UIHostingController(rootView: DigiaHostWrapperView())
        hc.view.tag = mountTag
        hc.view.translatesAutoresizingMaskIntoConstraints = false
        hc.view.backgroundColor = .clear
        // Pass touches through to React Native content below.
        hc.view.isUserInteractionEnabled = false

        rootVC.addChild(hc)
        rootVC.view.addSubview(hc.view)
        hc.didMove(toParent: rootVC)

        NSLayoutConstraint.activate([
            hc.view.leadingAnchor.constraint(equalTo: rootVC.view.leadingAnchor),
            hc.view.trailingAnchor.constraint(equalTo: rootVC.view.trailingAnchor),
            hc.view.topAnchor.constraint(equalTo: rootVC.view.topAnchor),
            hc.view.bottomAnchor.constraint(equalTo: rootVC.view.bottomAnchor),
        ])
    }

    // ────────────────────────────────────────────────────────────────────────
    // MARK: - Private helpers

    private func buildInAppPayloadContent(from map: NSDictionary) -> InAppPayloadContent {
        let pk      = map["placementKey"]  as? String
        let title   = map["title"]         as? String
        let text    = map["text"]          as? String
        let viewId  = map["viewId"]        as? String
        let command = map["command"]       as? String
        let screenId = map["screenId"]     as? String
        var type = (map["type"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if type.isEmpty {
            type = (pk?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "").isEmpty ? "dialog" : "inline"
        }
        let args: [String: JSONValue] = {
            guard let raw = map["args"] as? [String: Any] else { return [:] }
            return raw.compactMapValues { JSONValue(rawValue: $0) }
        }()

        return InAppPayloadContent(
            type: type,
            placementKey: pk,
            title: title,
            text: text,
            viewId: viewId,
            command: command,
            args: args,
            screenId: screenId
        )
    }
}

// MARK: - JSONValue convenience init from Any
private extension JSONValue {
    init?(rawValue: Any) {
        switch rawValue {
        case let s as String:  self = .string(s)
        case let b as Bool:    self = .bool(b)
        case let i as Int:     self = .int(i)
        case let d as Double:  self = .double(d)
        case let arr as [Any]:
            self = .array(arr.compactMap { JSONValue(rawValue: $0) })
        case let dict as [String: Any]:
            let mapped = dict.compactMapValues { JSONValue(rawValue: $0) }
            self = .object(mapped)
        default:
            return nil
        }
    }
}

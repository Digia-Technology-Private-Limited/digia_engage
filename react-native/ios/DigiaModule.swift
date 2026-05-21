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
 *   createInitialPage(): void  // AppConfig initial route
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
    // MARK: - showAnchoredOverlay

    /// Shows SHOW_TOOLTIP or SHOW_SPOTLIGHT anchored to coordinates measured in JS.
    /// anchorX/Y/Width/Height are screen-pixel values from measureInWindow().
    @objc
    func showAnchoredOverlay(
        _ id: String,
        content contentMap: NSDictionary,
        cepContext cepContextMap: NSDictionary,
        anchorX: Double,
        anchorY: Double,
        anchorWidth: Double,
        anchorHeight: Double
    ) {
        let mutable = NSMutableDictionary(dictionary: contentMap)
        // Pack anchor coords into the args sub-dict so buildInAppPayloadContent
        // captures them as JSONValue entries in InAppPayloadContent.args.
        var argsDict = (mutable["args"] as? [String: Any]) ?? [:]
        argsDict["_anchorX"] = anchorX
        argsDict["_anchorY"] = anchorY
        argsDict["_anchorWidth"] = anchorWidth
        argsDict["_anchorHeight"] = anchorHeight
        mutable["args"] = argsDict

        let content = buildInAppPayloadContent(from: mutable)
        let cepContext = (cepContextMap as? [String: String]) ?? [:]
        let payload = InAppPayload(id: id, content: content, cepContext: cepContext)

        Task { @MainActor in
            guard let delegate = self.rnPlugin.delegate else { return }
            delegate.onCampaignTriggered(payload)
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

        // Container with passthrough hitTest — lets tooltip card taps reach SwiftUI
        // while all other touches fall through to React Native content.
        let container = DigiaPassthroughHostView()
        container.tag = mountTag
        container.translatesAutoresizingMaskIntoConstraints = false
        container.backgroundColor = .clear

        let hc = UIHostingController(rootView: DigiaHostWrapperView())
        hc.view.translatesAutoresizingMaskIntoConstraints = false
        hc.view.backgroundColor = .clear

        container.hostingView = hc.view

        rootVC.addChild(hc)
        container.addSubview(hc.view)
        hc.didMove(toParent: rootVC)

        rootVC.view.addSubview(container)

        NSLayoutConstraint.activate([
            container.leadingAnchor.constraint(equalTo: rootVC.view.leadingAnchor),
            container.trailingAnchor.constraint(equalTo: rootVC.view.trailingAnchor),
            container.topAnchor.constraint(equalTo: rootVC.view.topAnchor),
            container.bottomAnchor.constraint(equalTo: rootVC.view.bottomAnchor),
            hc.view.leadingAnchor.constraint(equalTo: container.leadingAnchor),
            hc.view.trailingAnchor.constraint(equalTo: container.trailingAnchor),
            hc.view.topAnchor.constraint(equalTo: container.topAnchor),
            hc.view.bottomAnchor.constraint(equalTo: container.bottomAnchor),
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

        let anchorKey = map["anchorKey"] as? String

        return InAppPayloadContent(
            type: type,
            placementKey: pk,
            title: title,
            text: text,
            viewId: viewId,
            command: command,
            args: args,
            screenId: screenId,
            anchorKey: anchorKey
        )
    }
}

// MARK: - DigiaPassthroughHostView

/// Container that delegates hitTest to the SwiftUI hosting view.
/// When SwiftUI renders nothing interactive (no overlay), hitTest returns nil
/// so touches fall through to React Native. When an overlay is visible,
/// taps on it are consumed by SwiftUI.
private final class DigiaPassthroughHostView: UIView {
    weak var hostingView: UIView?

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        guard let hv = hostingView else { return nil }
        let converted = convert(point, to: hv)
        return hv.hitTest(converted, with: event)
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

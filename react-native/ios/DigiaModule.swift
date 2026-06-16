import DigiaEngage
/**
 * DigiaModule
 *
 * React Native NativeModule that bridges the Digia Engage iOS SDK.
 *
 * Exposed methods (callable from JS via NativeModules.DigiaEngageModule):
 *   initialize(projectId, environment, logLevel, baseUrl): Promise<void>
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
        _ projectId: String,
        environment: String,
        logLevel: String,
        baseUrl: String?,
        fontFamily: String?,
        resolve: @escaping RCTPromiseResolveBlock,
        reject: @escaping RCTPromiseRejectBlock
    ) {
        let envValue: DigiaEnvironment =
            environment.lowercased() == "sandbox" ? .sandbox : .production
        let logLevelValue: DigiaLogLevel
        switch logLevel.lowercased() {
        case "verbose": logLevelValue = .verbose
        case "none": logLevelValue = .none
        default: logLevelValue = .error
        }

        let cleanBaseUrl = baseUrl.flatMap { url -> String? in
            var s = url.trimmingCharacters(in: .whitespacesAndNewlines)
                .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            if s.hasSuffix("/api/v1") { s = String(s.dropLast(7)) }
            return s.isEmpty ? nil : s
        }

        let config = DigiaConfig(
            apiKey: projectId,
            logLevel: logLevelValue,
            environment: envValue,
            developerConfig: cleanBaseUrl.map { DigiaDeveloperConfig(baseURL: $0) },
            fontFamily: fontFamily.flatMap { $0.isEmpty ? nil : $0 }
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
            // Dismiss any stale overlay left over from the previous JS session.
            Digia.dismissActiveNudge()

            // After a JS reload, re-bring the overlay host to the front.
            // Expo/RN may have added views during the reload cycle that sit on
            // top of the host, causing the SwiftUI layer to be unreachable by
            // touch even when hasActiveOverlay = true.
            if let rootVC = UIApplication.shared.connectedScenes
                .compactMap({ ($0 as? UIWindowScene)?.keyWindow?.rootViewController })
                .first,
               let hostView = rootVC.view.viewWithTag(Self.overlayMountTag) {
                rootVC.view.bringSubviewToFront(hostView)
            }

            Digia.register(self.rnPlugin)
            print("[DigiaRN] registerBridge: rnPlugin registered, delegate=\(self.rnPlugin.delegate != nil ? "set" : "nil")")
        }
    }

    private static let overlayMountTag = 0xD19140

    // ────────────────────────────────────────────────────────────────────────
    // MARK: - setCurrentScreen

    @objc
    func setCurrentScreen(_ name: String) {
        // Digia.setCurrentScreen is not yet part of the public iOS API; this is
        // a no-op placeholder that can be activated once it is added.
        // Digia.setCurrentScreen(name)
    }

    // ────────────────────────────────────────────────────────────────────────
    // MARK: - Analytics identity

    @objc
    func setUserId(_ userId: String) {
        Task { @MainActor in Digia.setUserId(userId) }
    }

    @objc
    func clearUserId() {
        Task { @MainActor in Digia.clearUserId() }
    }

    // ────────────────────────────────────────────────────────────────────────
    // MARK: - trackEvent (guide / JS-rendered campaigns)

    @objc
    func trackEvent(
        _ eventType: String,
        campaignId: String,
        campaignKey: String,
        campaignType: String,
        elementId: String?
    ) {
        print("[DigiaAnalytics] [DigiaRN] trackEvent type=\(eventType) campaignId=\(campaignId) campaignKey=\(campaignKey) campaignType=\(campaignType) elementId=\(elementId ?? "nil")")

        let event: DigiaExperienceEvent
        switch eventType.lowercased() {
        case "impressed", "viewed": event = .impressed
        case "clicked": event = .clicked(elementID: elementId)
        case "dismissed": event = .dismissed
        default:
            print("[DigiaAnalytics] [DigiaRN] trackEvent: unknown eventType '\(eventType)' — dropping")
            return
        }

        let payload = InAppPayload(
            id: campaignId,
            content: InAppPayloadContent(
                type: campaignType,
                args: [
                    "campaign_id": .string(campaignId),
                    "campaign_key": .string(campaignKey),
                    "campaign_type": .string(campaignType),
                ],
                campaignKey: campaignKey
            )
        )

        Task { @MainActor in
            Digia.captureAnalyticsEvent(event, payload: payload)
        }
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
        print("[DigiaRN] triggerCampaign id=\(id) campaignKey=\(content.campaignKey ?? "nil")")

        Task { @MainActor in
            guard let delegate = self.rnPlugin.delegate else {
                print("[DigiaRN] triggerCampaign: delegate is nil — registerBridge() may not have run yet")
                return
            }
            delegate.onCampaignTriggered(payload)
            Task { @MainActor in
                print("[DigiaRN] triggerCampaign post-call: hasActiveOverlay=\(Digia.hasActiveOverlay)")
            }
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
    // MARK: - registerAnchor / unregisterAnchor

    /// Registers a UI element as an anchor point for Guide experiences.
    /// JS sends physical pixels; convert to UIKit points using screen scale.
    @objc
    func registerAnchor(_ key: String, x: Double, y: Double, width: Double, height: Double) {
        let scale = UIScreen.main.scale
        let rect = CGRect(
            x: CGFloat(x) / scale,
            y: CGFloat(y) / scale,
            width: CGFloat(width) / scale,
            height: CGFloat(height) / scale
        )
        Task { @MainActor in
            AnchorRegistry.shared.register(key: key, rect: rect)
        }
    }

    @objc
    func unregisterAnchor(_ key: String) {
        Task { @MainActor in
            AnchorRegistry.shared.unregister(key: key)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // MARK: - getRegisteredComponents

    @objc
    func getRegisteredComponents(
        _ resolve: @escaping RCTPromiseResolveBlock,
        reject: @escaping RCTPromiseRejectBlock
    ) {
        resolve([])
    }

    // ────────────────────────────────────────────────────────────────────────
    // MARK: - Internal: mount the SwiftUI overlay host

    /// Mirrors Android's DigiaModule.mountDigiaHost().
    /// Called once after Digia.initialize() succeeds — no need for a manual
    /// <DigiaHostView> anywhere in the JS component tree.
    @MainActor
    private func mountDigiaHost() {
        // Locate the key window's root view controller.
        guard
            let rootVC = UIApplication.shared
                .connectedScenes
                .compactMap({ ($0 as? UIWindowScene)?.keyWindow })
                .first?
                .rootViewController
        else { return }

        // Guard against double-mounting (e.g. fast-refresh).
        if rootVC.view.viewWithTag(Self.overlayMountTag) != nil { return }

        let hc = UIHostingController(rootView: DigiaHostWrapperView())
        let hostView = DigiaRootOverlayView(hostingController: hc)
        hostView.tag = Self.overlayMountTag
        hostView.translatesAutoresizingMaskIntoConstraints = false
        hostView.backgroundColor = .clear

        hc.view.translatesAutoresizingMaskIntoConstraints = false
        hc.view.backgroundColor = .clear

        rootVC.addChild(hc)
        hostView.addSubview(hc.view)
        rootVC.view.addSubview(hostView)
        hc.didMove(toParent: rootVC)

        NSLayoutConstraint.activate([
            hostView.leadingAnchor.constraint(equalTo: rootVC.view.leadingAnchor),
            hostView.trailingAnchor.constraint(equalTo: rootVC.view.trailingAnchor),
            hostView.topAnchor.constraint(equalTo: rootVC.view.topAnchor),
            hostView.bottomAnchor.constraint(equalTo: rootVC.view.bottomAnchor),
            hc.view.leadingAnchor.constraint(equalTo: hostView.leadingAnchor),
            hc.view.trailingAnchor.constraint(equalTo: hostView.trailingAnchor),
            hc.view.topAnchor.constraint(equalTo: hostView.topAnchor),
            hc.view.bottomAnchor.constraint(equalTo: hostView.bottomAnchor),
        ])
    }

    // ────────────────────────────────────────────────────────────────────────
    // MARK: - Private helpers

    private func buildInAppPayloadContent(from map: NSDictionary) -> InAppPayloadContent {
        let pk = map["placementKey"] as? String
        let title = map["title"] as? String
        let text = map["text"] as? String
        let viewId = map["viewId"] as? String
        let command = map["command"] as? String
        let screenId = map["screenId"] as? String
        let campaignKey =
            (map["campaignKey"] as? String) ?? (map["campaign_key"] as? String)
            ?? (map["digia_campaign_key"] as? String)
            ?? (map["digiaKey"] as? String)
        var type = (map["type"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if type.isEmpty {
            type =
                (pk?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "").isEmpty
                ? "dialog" : "inline"
        }
        let args: [String: JSONValue] = {
            guard let raw = map["args"] as? [String: Any] else { return [:] }
            return raw.compactMapValues { JSONValue(rawValue: $0) }
        }()

        // CEP trigger variables for `{{ }}` interpolation. CleverTap's nudge
        // mapper puts them at `content.variables` (top-level); the command/inline
        // mappers may nest them under `args.variables`. Mirror the JS bridge's
        // `_extractVariables` (content.variables first, then args.variables).
        let variables = Self.variableMap(map["variables"])
            ?? Self.variableMap((map["args"] as? [String: Any])?["variables"])

        return InAppPayloadContent(
            type: type,
            placementKey: pk,
            title: title,
            text: text,
            viewId: viewId,
            command: command,
            args: args,
            screenId: screenId,
            campaignKey: campaignKey,
            variables: variables
        )
    }

    /// Coerces a raw `variables` value into a `[String: String]` map, stringifying
    /// scalar values (string / number / bool) and dropping anything else. Returns
    /// nil for a missing or empty map. Mirrors the JS `parseVariableMap`.
    private static func variableMap(_ raw: Any?) -> [String: String]? {
        guard let dict = raw as? [String: Any] else { return nil }
        var result: [String: String] = [:]
        for (key, value) in dict {
            switch value {
            case let string as String:
                result[key] = string
            case let number as NSNumber:
                // Distinguish a boxed bool from a numeric NSNumber so `true`
                // stays "true" rather than "1".
                if CFGetTypeID(number) == CFBooleanGetTypeID() {
                    result[key] = number.boolValue ? "true" : "false"
                } else {
                    result[key] = number.stringValue
                }
            default:
                continue
            }
        }
        return result.isEmpty ? nil : result
    }
}

// MARK: - DigiaRootOverlayView

/// Full-screen native overlay container for the auto-mounted DigiaHost.
/// It stays touch-transparent when SwiftUI has no active overlay, but lets
/// survey/dialog/sheet content and barriers receive touches when present.
private final class DigiaRootOverlayView: UIView {
    private let hostingController: UIHostingController<DigiaHostWrapperView>

    init(hostingController: UIHostingController<DigiaHostWrapperView>) {
        self.hostingController = hostingController
        super.init(frame: .zero)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        guard let hcView = hostingController.view else { return nil }

        // When a modal overlay (survey / dialog / bottom sheet / toast / tooltip)
        // is active, the SwiftUI layer must own ALL touches so the dim backdrop
        // both blocks the app underneath and receives barrier-dismiss taps.
        // SwiftUI draws a gesture-only scrim (Color + .onTapGesture) into the
        // hosting view itself with no child UIView, so the `hit === hcView`
        // heuristic below would wrongly treat it as empty and pass the touch
        // through to React Native content.
        let overlayActive = MainActor.assumeIsolated { Digia.hasActiveOverlay }
        if overlayActive {
            return hcView.hitTest(point, with: event) ?? hcView
        }

        let hit = hcView.hitTest(point, with: event)
        if hit == nil || hit === hcView { return nil }
        return hit
    }
}

// MARK: - JSONValue convenience init from Any
extension JSONValue {
    fileprivate init?(rawValue: Any) {
        switch rawValue {
        case let s as String: self = .string(s)
        case let b as Bool: self = .bool(b)
        case let i as Int: self = .int(i)
        case let d as Double: self = .double(d)
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

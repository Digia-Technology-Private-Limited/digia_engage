import Foundation
import SDWebImage
import SDWebImageSVGCoder
#if canImport(UIKit)
import UIKit
#endif
#if os(macOS)
import AppKit
#endif

@MainActor
final class DigiaRuntime: ObservableObject, DigiaCEPDelegate {
    static let shared = DigiaRuntime()
    private static var imageCodersConfigured = false

    @Published private(set) var config: DigiaConfig?
    @Published private(set) var currentScreen: String?
    @Published private(set) var sdkState: SDKState = .notInitialized
    @Published private(set) var isHostMounted = false
    @Published private(set) var appState: [String: ScopeValue] = [:]

    private var activePlugin: DigiaCEPPlugin?
    private(set) var fontFactory: DUIFontFactory = DefaultFontFactory()
    private var messageSubscribers: [String: [UUID: @Sendable (ScopeValue?) -> Void]] = [:]
    private var appStateStore: AppStateStore?
    private var localStateStores: [String: LocalStateStore] = [:]

    let appConfigStore = AppConfigStore()
    let controller = DigiaOverlayController()
    let inlineController = InlineCampaignController()
    let navigationController = DigiaNavigationController()

    private(set) var appStateStreams: [String: AppStateValueStream] = [:]
    private(set) var lastOpenedURL: URL?
    private(set) var clipboardString: String?
    private(set) var lastShareRequest: (message: String, subject: String?)?
    private(set) var lastDialogDismissed = false
    private(set) var lastBottomSheetDismissed = false

    private init() {
        if !Self.imageCodersConfigured {
            SDImageCodersManager.shared.addCoder(SDImageSVGCoder.shared)
            Self.imageCodersConfigured = true
        }

        controller.onEvent = { [weak self] event, payload in
            self?.activePlugin?.notifyEvent(event, payload: payload)
        }

        inlineController.onEvent = { [weak self] event, payload in
            self?.activePlugin?.notifyEvent(event, payload: payload)
        }
    }

    func initialize(_ config: DigiaConfig) {
        guard self.config == nil else { return }
        self.config = config

        if let appConfig = try? DigiaConfigResolver(config: config).getConfig() {
            appConfigStore.update(appConfig)
            navigationController.setInitialRoute(appConfig.initialRoute)
            do {
                try initializeAppState(from: appConfig, namespace: config.apiKey)
            } catch {
                appConfigStore.setError(String(describing: error))
            }
        } else {
            appConfigStore.setLoading()
            Task { @MainActor in
                do {
                    let appConfig = try await DigiaConfigResolver(config: config).getConfigAsync()
                    self.appConfigStore.update(appConfig)
                    self.navigationController.setInitialRoute(appConfig.initialRoute)
                    try self.initializeAppState(from: appConfig, namespace: config.apiKey)
                } catch {
                    self.appConfigStore.setError(String(describing: error))
                }
            }
        }

        sdkState = .ready
    }

    func register(_ plugin: DigiaCEPPlugin) {
        activePlugin?.teardown()
        activePlugin = plugin
        plugin.setup(delegate: self)
    }

    func setCurrentScreen(_ name: String) {
        currentScreen = name
        activePlugin?.forwardScreen(name)
    }

    func registerFontFactory(_ factory: DUIFontFactory) {
        fontFactory = factory
    }

    func registerPlaceholderForSlot(screenName: String, propertyID: String) -> Int? {
        activePlugin?.registerPlaceholder(screenName: screenName, propertyID: propertyID)
    }

    func deregisterPlaceholderForSlot(_ id: Int) {
        activePlugin?.deregisterPlaceholder(id)
    }

    func onHostMounted() {
        isHostMounted = true
    }

    func onHostUnmounted() {
        isHostMounted = false
    }

    func onCampaignTriggered(_ payload: InAppPayload) {
        let displayType = payload.content.type.lowercased()
        let placementID = payload.content.placementId

        if displayType == "inline", let placementID {
            inlineController.setCampaign(placementID, payload: payload)
        } else {
            controller.show(payload)
        }
    }

    func onCampaignInvalidated(_ campaignID: String) {
        if controller.activePayload?.id == campaignID {
            controller.dismiss()
        }
        inlineController.removeCampaign(campaignID)
    }

    func resetForTesting() {
        activePlugin?.teardown()
        activePlugin = nil
        config = nil
        currentScreen = nil
        sdkState = .notInitialized
        isHostMounted = false
        fontFactory = DefaultFontFactory()
        appConfigStore.clear()
        controller.dismiss()
        controller.dismissBottomSheet()
        controller.dismissDialog()
        controller.dismissToast()
        controller.clearSlots()
        inlineController.clear()
        navigationController.reset()
        messageSubscribers.removeAll()
        appStateStore = nil
        appState.removeAll()
        appStateStreams.removeAll()
        localStateStores.removeAll()
        lastOpenedURL = nil
        clipboardString = nil
        lastShareRequest = nil
        lastDialogDismissed = false
        lastBottomSheetDismissed = false
    }

    @discardableResult
    func addMessageListener(name: String, listener: @escaping @Sendable (ScopeValue?) -> Void) -> UUID {
        let token = UUID()
        var listeners = messageSubscribers[name, default: [:]]
        listeners[token] = listener
        messageSubscribers[name] = listeners
        return token
    }

    func removeMessageListener(name: String, token: UUID) {
        guard var listeners = messageSubscribers[name] else { return }
        listeners.removeValue(forKey: token)
        messageSubscribers[name] = listeners
    }

    func publishMessage(name: String, payload: ScopeValue?) {
        messageSubscribers[name]?.values.forEach { listener in
            listener(payload)
        }
    }

    func setAppState(key: String, value: ScopeValue) throws {
        guard let appStateStore else {
            throw AppStateStoreError.missingKey(key)
        }
        try appStateStore.update(key: key, value: value)
        appState = appStateStore.snapshot()
        appStateStreams[key]?.publish(value.anyValue)
        if let streamName = appStateStore.streamName(for: key) {
            appStateStreams[streamName]?.publish(value.anyValue)
        }
    }

    func registerLocalStateStore(_ store: LocalStateStore) {
        guard let namespace = store.namespace, !namespace.isEmpty else { return }
        localStateStores[namespace] = store
    }

    func unregisterLocalStateStore(_ store: LocalStateStore) {
        guard let namespace = store.namespace, localStateStores[namespace] === store else { return }
        localStateStores.removeValue(forKey: namespace)
    }

    func localStateStore(named namespace: String) -> LocalStateStore? {
        localStateStores[namespace]
    }

    func openURL(_ url: URL) {
        lastOpenedURL = url
        #if canImport(UIKit)
        UIApplication.shared.open(url)
        #elseif os(macOS)
        NSWorkspace.shared.open(url)
        #endif
    }

    func copyToClipboard(_ text: String) {
        clipboardString = text
        #if canImport(UIKit)
        UIPasteboard.general.string = text
        #elseif os(macOS)
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(text, forType: .string)
        #endif
    }

    func share(message: String, subject: String?) {
        lastShareRequest = (message, subject)
    }

    func didDismissDialog() {
        lastDialogDismissed = true
    }

    func didDismissBottomSheet() {
        lastBottomSheetDismissed = true
    }

    private func initializeAppState(from appConfig: DigiaAppConfig, namespace: String) throws {
        appState.removeAll()
        appStateStreams.removeAll()
        let definitions = appConfig.appState ?? []
        let store = try AppStateStore(definitions: definitions, namespace: namespace)
        appStateStore = store
        appState = store.snapshot()
        for definition in definitions {
            let stream = AppStateValueStream(currentValue: appState[definition.name]?.anyValue)
            appStateStreams[definition.streamName] = stream
        }
    }
}

@MainActor
final class InlineCampaignController: ObservableObject {
    @Published private var campaigns: [String: InAppPayload] = [:]
    var onEvent: ((DigiaExperienceEvent, InAppPayload) -> Void)?

    func getCampaign(_ placementKey: String) -> InAppPayload? {
        campaigns[placementKey]
    }

    func setCampaign(_ placementKey: String, payload: InAppPayload) {
        campaigns[placementKey] = payload
    }

    func removeCampaign(_ campaignID: String) {
        campaigns = campaigns.filter { placementKey, payload in
            placementKey != campaignID && payload.id != campaignID
        }
    }

    func dismissCampaign(_ placementKey: String) {
        campaigns.removeValue(forKey: placementKey)
    }

    func clear() {
        campaigns.removeAll()
    }
}

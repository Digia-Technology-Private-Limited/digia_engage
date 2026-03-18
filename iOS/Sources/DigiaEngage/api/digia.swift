import Foundation

@MainActor
public enum Digia {
    public static func initialize(_ config: DigiaConfig) {
        DigiaRuntime.shared.initialize(config)
    }

    public static func register(_ plugin: DigiaCEPPlugin) {
        DigiaRuntime.shared.register(plugin)
    }

    public static func setCurrentScreen(_ name: String) {
        DigiaRuntime.shared.setCurrentScreen(name)
    }

    public static func registerFontFactory(_ factory: DUIFontFactory) {
        DigiaRuntime.shared.registerFontFactory(factory)
    }

    @discardableResult
    public static func onMessage(
        _ name: String,
        listener: @escaping @Sendable (ScopeValue?) -> Void
    ) -> UUID {
        DigiaRuntime.shared.addMessageListener(name: name, listener: listener)
    }

    public static func removeMessageListener(_ name: String, token: UUID) {
        DigiaRuntime.shared.removeMessageListener(name: name, token: token)
    }
}

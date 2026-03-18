import Foundation

final class LocalStateStore: ObservableObject {
    let namespace: String?
    private let parent: LocalStateStore?
    // Intentionally NOT @Published.
    // We want `setValues(..., notify: false)` to mutate state without triggering SwiftUI updates.
    // Rebuilds should only happen via explicit `objectWillChange.send()` (notify=true or rebuildState).
    private(set) var stateVariables: [String: ScopeValue]

    init(namespace: String?, initialState: [String: ScopeValue], parent: LocalStateStore? = nil) {
        self.namespace = namespace
        self.stateVariables = initialState
        self.parent = parent
    }

    func getValue(_ key: String) -> ScopeValue? {
        if let value = stateVariables[key] {
            return value
        }
        return parent?.getValue(key)
    }

    func hasKey(_ key: String) -> Bool {
        stateVariables[key] != nil
    }

    func setValues(_ updates: [String: ScopeValue], notify: Bool) {
        guard !updates.isEmpty else { return }
        var changed = false
        for (key, value) in updates where stateVariables[key] != nil {
            if stateVariables[key] != value {
                stateVariables[key] = value
                changed = true
            }
        }
        if notify, changed {
            objectWillChange.send()
        }
    }

    func triggerListeners() {
        objectWillChange.send()
    }

    func findAncestorContext(_ targetNamespace: String) -> LocalStateStore? {
        if namespace == targetNamespace {
            return self
        }
        return parent?.findAncestorContext(targetNamespace)
    }
}

import Foundation

struct RebuildStateAction: Sendable {
    let actionType: ActionType = .rebuildState
    let disableActionIf: ExprOr<Bool>?
    let data: [String: ScopeValue]
}

@MainActor
struct RebuildStateProcessor {
    let processorType: ActionType = .rebuildState

    func execute(action: RebuildStateAction, context: ActionProcessorContext) async throws {
        let targetStore: LocalStateStore?
        if let name = action.data.string("stateContextName") {
            targetStore = DigiaRuntime.shared.localStateStore(named: name)
        } else {
            targetStore = context.localStateStore
        }
        guard let targetStore else {
            throw ActionExecutionError.unsupportedContext(processorType)
        }
        targetStore.triggerListeners()
    }
}

import Foundation

struct ControlObjectAction: Sendable {
    let actionType: ActionType = .controlObject
    let disableActionIf: ExprOr<Bool>?
    let data: [String: ScopeValue]
}

@MainActor
struct ControlObjectProcessor {
    let processorType: ActionType = .controlObject
    func execute(action _: ControlObjectAction, context _: ActionProcessorContext) async throws {
        throw ActionExecutionError.unsupportedContext(processorType)
    }
}

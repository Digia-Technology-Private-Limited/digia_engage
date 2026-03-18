import Foundation

struct ExecuteCallbackAction: Sendable {
    let actionType: ActionType = .executeCallback
    let disableActionIf: ExprOr<Bool>?
    let data: [String: ScopeValue]
}

@MainActor
struct ExecuteCallbackProcessor {
    let processorType: ActionType = .executeCallback
    func execute(action _: ExecuteCallbackAction, context _: ActionProcessorContext) async throws {
        throw ActionExecutionError.unsupportedContext(processorType)
    }
}

import Foundation

struct PostMessageAction: Sendable {
    let actionType: ActionType = .postMessage
    let disableActionIf: ExprOr<Bool>?
    let data: [String: ScopeValue]
}

@MainActor
struct PostMessageProcessor {
    let processorType: ActionType = .postMessage

    func execute(action: PostMessageAction, context _: ActionProcessorContext) async throws {
        guard let name = action.data.string("name")
        else { throw ActionExecutionError.unsupportedContext(processorType) }
        DigiaRuntime.shared.publishMessage(name: name, payload: action.data["payload"] ?? action.data["body"])
    }
}

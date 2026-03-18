import Foundation

struct CopyToClipBoardAction: Sendable {
    let actionType: ActionType = .copyToClipBoard
    let disableActionIf: ExprOr<Bool>?
    let data: [String: ScopeValue]
}

@MainActor
struct CopyToClipBoardProcessor {
    let processorType: ActionType = .copyToClipBoard

    func execute(action: CopyToClipBoardAction, context: ActionProcessorContext) async throws {
        guard let text = (ScopeValueResolver.resolveAny(action.data["message"], in: context.scopeContext) as? String)
            ?? (ScopeValueResolver.resolveAny(action.data["text"], in: context.scopeContext) as? String)
            ?? (ScopeValueResolver.resolveAny(action.data["value"], in: context.scopeContext) as? String)
        else {
            throw ActionExecutionError.unsupportedContext(processorType)
        }
        DigiaRuntime.shared.copyToClipboard(text)
    }
}

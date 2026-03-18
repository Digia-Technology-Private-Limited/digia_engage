import Foundation

struct OpenUrlAction: Sendable {
    let actionType: ActionType = .openUrl
    let disableActionIf: ExprOr<Bool>?
    let data: [String: ScopeValue]
}

@MainActor
struct OpenUrlProcessor {
    let processorType: ActionType = .openUrl

    func execute(action: OpenUrlAction, context: ActionProcessorContext) async throws {
        guard let rawURL = ScopeValueResolver.resolveAny(action.data["url"], in: context.scopeContext) as? String,
              let url = URL(string: rawURL),
              url.scheme != nil
        else { throw ActionExecutionError.unsupportedContext(processorType) }
        DigiaRuntime.shared.openURL(url)
    }
}

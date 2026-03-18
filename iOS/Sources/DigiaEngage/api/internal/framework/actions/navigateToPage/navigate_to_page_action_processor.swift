import Foundation

struct NavigateToPageAction: Sendable {
    let actionType: ActionType = .navigateToPage
    let disableActionIf: ExprOr<Bool>?
    let data: [String: ScopeValue]
}

@MainActor
struct NavigateToPageProcessor {
    let processorType: ActionType = .navigateToPage

    func execute(action: NavigateToPageAction, context: ActionProcessorContext) async throws {
        let pageData = action.data.object("pageData")
        let pageID = pageData?.string("id") ?? action.data.string("pageId") ?? action.data.string("id")
        guard let pageID, context.appConfig.page(pageID) != nil else {
            throw ActionExecutionError.unsupportedContext(processorType)
        }
        let removePrevious = (ScopeValueResolver.resolveAny(action.data["shouldRemovePreviousScreensInStack"], in: context.scopeContext) as? Bool) ?? false
        if removePrevious {
            DigiaRuntime.shared.navigationController.replaceStack(with: pageID)
        } else {
            DigiaRuntime.shared.navigationController.push(pageID)
        }
    }
}

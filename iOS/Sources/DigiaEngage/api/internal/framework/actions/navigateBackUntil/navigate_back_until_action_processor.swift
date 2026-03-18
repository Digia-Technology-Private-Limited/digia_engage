import Foundation

struct NavigateBackUntilAction: Sendable {
    let actionType: ActionType = .navigateBackUntil
    let disableActionIf: ExprOr<Bool>?
    let data: [String: ScopeValue]
}

@MainActor
struct NavigateBackUntilProcessor {
    let processorType: ActionType = .navigateBackUntil

    func execute(action: NavigateBackUntilAction, context: ActionProcessorContext) async throws {
        let target = ScopeValueResolver.resolveAny(action.data["routeNameToPopUntil"], in: context.scopeContext) as? String
        guard let target else { throw ActionExecutionError.unsupportedContext(processorType) }
        let normalizedTarget = NavigationUtil.normalizedRoute(target)
        DigiaRuntime.shared.navigationController.popUntil { current in
            let normalizedCurrent = NavigationUtil.normalizedRoute(current)
            if normalizedCurrent == normalizedTarget { return true }
            if normalizedCurrent == normalizedTarget.trimmingCharacters(in: CharacterSet(charactersIn: "/")) { return true }
            if let page = context.appConfig.page(normalizedCurrent) {
                if page.slug == normalizedTarget || "/\(page.slug ?? "")" == normalizedTarget {
                    return true
                }
            }
            return false
        }
        #if canImport(UIKit)
        ViewControllerUtil.popToRoot()
        #endif
    }
}

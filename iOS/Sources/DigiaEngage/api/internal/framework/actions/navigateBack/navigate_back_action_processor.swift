import Foundation

struct NavigateBackAction: Sendable {
    let actionType: ActionType = .navigateBack
    let disableActionIf: ExprOr<Bool>?
    let data: [String: ScopeValue]
}

@MainActor
struct NavigateBackProcessor {
    let processorType: ActionType = .navigateBack

    func execute(action _: NavigateBackAction, context _: ActionProcessorContext) async throws {
        DigiaRuntime.shared.navigationController.pop()
        #if canImport(UIKit)
        ViewControllerUtil.popNavigation()
        #endif
    }
}

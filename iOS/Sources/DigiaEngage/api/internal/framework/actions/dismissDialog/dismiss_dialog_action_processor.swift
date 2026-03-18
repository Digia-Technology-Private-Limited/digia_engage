import Foundation

struct DismissDialogAction: Sendable {
    let actionType: ActionType = .dismissDialog
    let disableActionIf: ExprOr<Bool>?
    let data: [String: ScopeValue]
}

@MainActor
struct DismissDialogProcessor {
    let processorType: ActionType = .dismissDialog

    func execute(action _: DismissDialogAction, context _: ActionProcessorContext) async throws {
        DigiaRuntime.shared.controller.dismissDialog()
        DigiaRuntime.shared.didDismissDialog()
        #if canImport(UIKit)
        ViewControllerUtil.dismissPresented()
        #endif
    }
}

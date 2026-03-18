import Foundation

struct HideBottomSheetAction: Sendable {
    let actionType: ActionType = .hideBottomSheet
    let disableActionIf: ExprOr<Bool>?
    let data: [String: ScopeValue]
}

@MainActor
struct HideBottomSheetProcessor {
    let processorType: ActionType = .hideBottomSheet

    func execute(action _: HideBottomSheetAction, context _: ActionProcessorContext) async throws {
        DigiaRuntime.shared.controller.dismissBottomSheet()
        DigiaRuntime.shared.didDismissBottomSheet()
        #if canImport(UIKit)
        ViewControllerUtil.dismissPresented()
        #endif
    }
}

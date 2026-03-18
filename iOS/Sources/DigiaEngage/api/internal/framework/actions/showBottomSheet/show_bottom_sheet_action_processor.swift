import Foundation
#if canImport(UIKit)
import SwiftUI
import UIKit
#endif

struct ShowBottomSheetAction: Sendable {
    let actionType: ActionType = .showBottomSheet
    let disableActionIf: ExprOr<Bool>?
    let data: [String: ScopeValue]
}

@MainActor
struct ShowBottomSheetProcessor {
    let processorType: ActionType = .showBottomSheet
    func execute(action: ShowBottomSheetAction, context _: ActionProcessorContext) async throws {
        let viewData = action.data["viewData"]?.objectValue ?? [:]
        let viewID = viewData["id"]?.stringValue
            ?? action.data["componentId"]?.stringValue
            ?? action.data["pageId"]?.stringValue
            ?? UUID().uuidString

        let presentation = DigiaBottomSheetPresentation(
            view: DigiaViewPresentation(
                viewID: viewID,
                title: viewData["title"]?.stringValue ?? action.data["title"]?.stringValue,
                text: viewData["text"]?.stringValue ?? action.data["message"]?.stringValue
            )
        )

        DigiaRuntime.shared.controller.showBottomSheet(presentation)

        #if canImport(UIKit)
        let host = UIHostingController(rootView: DigiaPresentationView(presentation: presentation.view))
        host.modalPresentationStyle = .pageSheet
        if let sheet = host.sheetPresentationController {
            sheet.detents = [.medium(), .large()]
            sheet.prefersGrabberVisible = true
        }
        ViewControllerUtil.present(host)
        #endif
    }
}

private extension ScopeValue {
    var stringValue: String? {
        guard case let .string(value) = self else { return nil }
        return value
    }

    var objectValue: [String: ScopeValue]? {
        guard case let .object(value) = self else { return nil }
        return value
    }
}

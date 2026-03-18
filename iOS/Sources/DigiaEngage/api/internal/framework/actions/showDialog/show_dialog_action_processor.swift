import Foundation
#if canImport(UIKit)
import SwiftUI
import UIKit
#endif

struct ShowDialogAction: Sendable {
    let actionType: ActionType = .showDialog
    let disableActionIf: ExprOr<Bool>?
    let data: [String: ScopeValue]
}

@MainActor
struct ShowDialogProcessor {
    let processorType: ActionType = .showDialog

    func execute(action: ShowDialogAction, context: ActionProcessorContext) async throws {
        let viewData = action.data.object("viewData") ?? [:]
        let viewID = viewData.string("id") ?? action.data.string("componentId") ?? action.data.string("pageId")
        guard let viewID else { throw ActionExecutionError.unsupportedContext(processorType) }
        let barrierDismissible = (ScopeValueResolver.resolveAny(action.data["barrierDismissible"], in: context.scopeContext) as? Bool) ?? true
        let presentation = DigiaDialogPresentation(
            view: DigiaViewPresentation(
                viewID: viewID,
                title: viewData.string("title") ?? action.data.string("title"),
                text: viewData.string("text") ?? action.data.string("message")
            ),
            barrierDismissible: barrierDismissible
        )
        DigiaRuntime.shared.controller.showDialog(presentation)

        #if canImport(UIKit)
        let root = ZStack {
            if barrierDismissible {
                Color.black.opacity(0.35)
                    .ignoresSafeArea()
                    .contentShape(Rectangle())
                    .onTapGesture {
                        DispatchQueue.main.async {
                            ViewControllerUtil.dismissPresented {
                                DigiaRuntime.shared.controller.dismissDialog()
                            }
                        }
                    }
            } else {
                Color.black.opacity(0.35)
                    .ignoresSafeArea()
            }
            DigiaPresentationView(presentation: presentation.view)
                .padding(24)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                .padding(24)
        }
        let host = UIHostingController(rootView: root)
        host.view.backgroundColor = .clear
        host.modalPresentationStyle = .overFullScreen
        ViewControllerUtil.present(host)
        #endif
    }
}

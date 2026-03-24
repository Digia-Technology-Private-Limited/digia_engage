import Foundation
#if canImport(UIKit)
import SwiftUI
import UIKit
#endif

struct ShowBottomSheetAction: Sendable {
    let actionType: ActionType = .showBottomSheet
    let disableActionIf: ExprOr<Bool>?
    let data: [String: JSONValue]
}

@MainActor
struct ShowBottomSheetProcessor {
    let processorType: ActionType = .showBottomSheet

    func execute(action: ShowBottomSheetAction, context: ActionProcessorContext) async throws {
        let viewData = action.data["viewData"]?.objectValue ?? [:]
        let viewID = viewData["id"]?.stringValue
            ?? action.data["componentId"]?.stringValue
            ?? action.data["pageId"]?.stringValue
        guard let viewID else { throw ActionExecutionError.unsupportedContext(processorType) }

        let waitForResult = (ExpressionUtil.evaluateNestedExpressionsToAny(
            action.data["waitForResult"], in: context.scopeContext
        ) as? Bool) ?? false

        let onResultFlow = action.data["onResult"]?.asActionFlow()
        let args = action.data["args"]?.objectValue ?? [:]

        let presentation = DigiaBottomSheetPresentation(
            view: DigiaViewPresentation(
                viewID: viewID,
                title: viewData["title"]?.stringValue ?? action.data["title"]?.stringValue,
                text: viewData["text"]?.stringValue ?? action.data["message"]?.stringValue,
                args: args
            )
        )
        SDKInstance.shared.controller.showBottomSheet(presentation)

        #if canImport(UIKit)
        let overlayController = SDKInstance.shared.controller
        let root = ZStack(alignment: .bottom) {
            Color.black.opacity(0.35)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture {
                    DispatchQueue.main.async {
                        ViewControllerUtil.dismissPresented {
                            overlayController.dismissBottomSheet()
                        }
                    }
                }

            DigiaPresentationView(presentation: presentation.view)
                .frame(maxWidth: .infinity, alignment: .topLeading)
        }
        let host = UIHostingController(rootView: root)
        host.view.backgroundColor = .clear
        host.modalPresentationStyle = .overFullScreen
        ViewControllerUtil.present(host)

        if waitForResult, onResultFlow != nil {
            let result = await withCheckedContinuation { (continuation: CheckedContinuation<JSONValue?, Never>) in
                overlayController.onBottomSheetDismissed = { value in
                    continuation.resume(returning: value)
                }
            }
            let resultContext = BasicExprContext(variables: ["result": result?.anyValue ?? NSNull()])
            if let scopeContext = context.scopeContext {
                resultContext.addContextAtTail(scopeContext)
            }
            await context.actionExecutor.executeNow(
                onResultFlow,
                appConfig: context.appConfig,
                scopeContext: resultContext,
                triggerType: "onResult",
                localStateStore: context.localStateStore
            )
        }
        #endif
    }
}

private extension JSONValue {
    var stringValue: String? {
        guard case let .string(value) = self else { return nil }
        return value
    }

    var objectValue: [String: JSONValue]? {
        guard case let .object(value) = self else { return nil }
        return value
    }

    func asActionFlow() -> ActionFlow? {
        guard case let .object(object) = self,
              case let .array(steps)? = object["steps"] else { return nil }
        let decodedSteps = steps.compactMap { item -> ActionStep? in
            guard case let .object(obj) = item,
                  let type = obj.string("type") else { return nil }
            return ActionStep(type: type, data: obj.object("data"), disableActionIf: nil)
        }
        return ActionFlow(steps: decodedSteps)
    }
}

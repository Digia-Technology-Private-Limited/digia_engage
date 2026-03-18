import Foundation
@testable import DigiaEngage
import Testing

@MainActor
@Suite("Overlay Action Processors")
struct OverlayActionProcessorTests {
    @Test("showToast uses default duration when absent")
    func showToastDefaultsDuration() async throws {
        DigiaRuntime.shared.resetForTesting()

        try await ShowToastProcessor().execute(
            action: ShowToastAction(
                disableActionIf: nil,
                data: ["message": .string("Saved")]
            ),
            context: context()
        )

        #expect(DigiaRuntime.shared.controller.activeToast?.message == "Saved")
        #expect(DigiaRuntime.shared.controller.activeToast?.durationSeconds == 2)
    }

    @Test("showBottomSheet maps componentId fallback")
    func showBottomSheetUsesComponentIdFallback() async throws {
        DigiaRuntime.shared.resetForTesting()

        try await ShowBottomSheetProcessor().execute(
            action: ShowBottomSheetAction(
                disableActionIf: nil,
                data: [
                    "componentId": .string("checkout_sheet"),
                    "title": .string("Checkout"),
                ]
            ),
            context: context()
        )

        #expect(DigiaRuntime.shared.controller.activeBottomSheet?.view.viewID == "checkout_sheet")
        #expect(DigiaRuntime.shared.controller.activeBottomSheet?.view.title == "Checkout")
    }

    private func context() -> ActionProcessorContext {
        ActionProcessorContext(
            appConfig: AppConfigStore(),
            widgetHierarchy: [],
            currentEntityId: nil
        )
    }
}

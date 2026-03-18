import SwiftUI

@MainActor
public struct DigiaSlot<Placeholder: View>: View {
    public let placementKey: String
    private let placeholder: Placeholder
    @ObservedObject private var controller = DigiaRuntime.shared.inlineController
    @State private var placeholderID: Int?
    @State private var impressedPayloadID: String?

    public init(
        _ placementKey: String,
        @ViewBuilder placeholder: () -> Placeholder
    ) {
        self.placementKey = placementKey
        self.placeholder = placeholder()
    }

    public var body: some View {
        Group {
            if let payload = controller.getCampaign(placementKey) {
                DigiaFallbackExperienceView(payload: payload, mode: .inline) {
                    controller.onEvent?(.dismissed, payload)
                    controller.dismissCampaign(placementKey)
                }
                .onAppear {
                    registerPlaceholderIfNeeded()
                    if impressedPayloadID != payload.id {
                        impressedPayloadID = payload.id
                        controller.onEvent?(.impressed, payload)
                    }
                }
            } else {
                placeholder
                    .onAppear {
                        registerPlaceholderIfNeeded()
                    }
            }
        }
        .onDisappear {
            if let placeholderID {
                DigiaRuntime.shared.deregisterPlaceholderForSlot(placeholderID)
                self.placeholderID = nil
            }
        }
    }

    private func registerPlaceholderIfNeeded() {
        guard placeholderID == nil, let screen = DigiaRuntime.shared.currentScreen else {
            return
        }
        placeholderID = DigiaRuntime.shared.registerPlaceholderForSlot(
            screenName: screen,
            propertyID: placementKey
        )
    }
}

@MainActor
public extension DigiaSlot where Placeholder == EmptyView {
    init(_ placementKey: String) {
        self.init(placementKey) { EmptyView() }
    }
}

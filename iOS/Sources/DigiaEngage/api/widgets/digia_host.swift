import SwiftUI

@MainActor
public struct DigiaHost<Content: View>: View {
    private let content: Content
    @ObservedObject private var controller = DigiaRuntime.shared.controller

    public init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    public var body: some View {
        ZStack {
            content

            if let payload = controller.activePayload {
                DigiaFallbackExperienceView(payload: payload, mode: .modal) {
                    controller.onEvent?(.dismissed, payload)
                    controller.dismiss()
                }
                .onAppear {
                    DigiaRuntime.shared.onHostMounted()
                    controller.onEvent?(.impressed, payload)
                }
            }

            VStack {
                Spacer()
                if let toast = controller.activeToast {
                    Text(toast.message)
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 14)
                        .background(Color.black)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .padding(.bottom, 32)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .animation(.easeInOut(duration: 0.2), value: controller.activeToast != nil)
        }
        .onAppear {
            DigiaRuntime.shared.onHostMounted()
        }
        .onDisappear {
            DigiaRuntime.shared.onHostUnmounted()
        }
    }
}

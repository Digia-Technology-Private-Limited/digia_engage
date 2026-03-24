import Foundation
#if canImport(UIKit)
import SwiftUI
import UIKit
#endif

enum NavigationUtil {
    static func normalizedRoute(_ value: String) -> String {
        value.trimmingCharacters(in: .whitespacesAndNewlines)
    }

#if canImport(UIKit)
    static func enableInteractivePopGestureIfNeeded(for navigationController: UINavigationController?) {
        guard let navigationController, navigationController.viewControllers.count > 1 else { return }
        guard let popGesture = navigationController.interactivePopGestureRecognizer else { return }
        popGesture.isEnabled = true
        popGesture.delegate = nil
    }
#endif
}

#if canImport(UIKit)
private struct DigiaInteractivePopGestureHost: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> DigiaInteractivePopGestureHostController {
        DigiaInteractivePopGestureHostController()
    }

    func updateUIViewController(_ uiViewController: DigiaInteractivePopGestureHostController, context: Context) {
        uiViewController.enableIfNeeded()
    }
}

private final class DigiaInteractivePopGestureHostController: UIViewController {
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        enableIfNeeded()
    }

    func enableIfNeeded() {
        NavigationUtil.enableInteractivePopGestureIfNeeded(for: navigationController)
    }
}

extension View {
    @ViewBuilder
    func digiaKeepSwipeBackGestureEnabled() -> some View {
        self.background(DigiaInteractivePopGestureHost().frame(width: 0, height: 0))
    }
}
#endif

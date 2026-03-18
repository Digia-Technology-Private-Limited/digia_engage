#if canImport(UIKit)
import SwiftUI
import UIKit

@MainActor
enum ViewControllerUtil {
    static func topViewController(base: UIViewController? = UIApplication.shared.connectedScenes
        .compactMap { $0 as? UIWindowScene }
        .flatMap { $0.windows }
        .first(where: { $0.isKeyWindow })?.rootViewController) -> UIViewController? {
        if let navigation = base as? UINavigationController {
            return topViewController(base: navigation.visibleViewController)
        }
        if let tab = base as? UITabBarController, let selected = tab.selectedViewController {
            return topViewController(base: selected)
        }
        if let presented = base?.presentedViewController {
            return topViewController(base: presented)
        }
        return base
    }

    static func present(_ controller: UIViewController, animated: Bool = true) {
        topViewController()?.present(controller, animated: animated)
    }

    static func dismissPresented(animated: Bool = true, completion: (() -> Void)? = nil) {
        topViewController()?.dismiss(animated: animated, completion: completion)
    }

    static func popNavigation(animated: Bool = true) {
        if let navigation = topViewController()?.navigationController {
            navigation.popViewController(animated: animated)
        } else {
            topViewController()?.dismiss(animated: animated)
        }
    }

    static func popToRoot(animated: Bool = true) {
        if let navigation = topViewController()?.navigationController {
            navigation.popToRootViewController(animated: animated)
        } else {
            topViewController()?.dismiss(animated: animated)
        }
    }
}
#endif

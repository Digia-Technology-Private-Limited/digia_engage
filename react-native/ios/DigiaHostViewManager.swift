/**
 * DigiaHostViewManager
 *
 * React Native ViewManager that exposes a UIView wrapping DigiaHost (from the
 * Digia iOS SDK) as the native view behind the JS <DigiaHostView> component.
 *
 * DigiaHost is a SwiftUI view, so we bridge it into UIKit using a
 * UIHostingController embedded as a child view controller.  The host view
 * manages dialog and bottom-sheet overlays driven by Digia CEP plugins.
 *
 * Place <DigiaHostView> once at the root of your RN component tree.
 */
import SwiftUI
import React
import DigiaEngage

@objc(DigiaHostView)
final class DigiaHostViewManager: RCTViewManager {

    override static func requiresMainQueueSetup() -> Bool { true }

    override func view() -> UIView! {
        return DigiaHostUIView()
    }
}

// MARK: - DigiaHostUIView

/// Lightweight UIView container that embeds a UIHostingController<DigiaHost>
/// as a child so SwiftUI's DigiaHost composable renders overlays above all
/// React Native content.
final class DigiaHostUIView: UIView {

    private var hostingController: UIHostingController<DigiaHostWrapperView>?

    override func didMoveToWindow() {
        super.didMoveToWindow()
        guard window != nil else {
            // View removed from hierarchy — tear down the hosting controller.
            hostingController?.willMove(toParent: nil)
            hostingController?.view.removeFromSuperview()
            hostingController?.removeFromParent()
            hostingController = nil
            return
        }
        guard hostingController == nil else { return }
        mountHostingController()
    }

    private func mountHostingController() {
        guard let parentVC = parentViewController() else { return }

        let swiftUIView = DigiaHostWrapperView()
        let hc = UIHostingController(rootView: swiftUIView)
        hc.view.translatesAutoresizingMaskIntoConstraints = false
        hc.view.backgroundColor = .clear
        // Disable touch interception so all taps pass through to React Native
        // content below. The dialog/bottom-sheet overlays are presented as
        // separate UIViewControllers (via ViewControllerUtil.present) so they
        // independently capture touches when visible.
        hc.view.isUserInteractionEnabled = false

        parentVC.addChild(hc)
        addSubview(hc.view)
        hc.didMove(toParent: parentVC)

        NSLayoutConstraint.activate([
            hc.view.leadingAnchor.constraint(equalTo: leadingAnchor),
            hc.view.trailingAnchor.constraint(equalTo: trailingAnchor),
            hc.view.topAnchor.constraint(equalTo: topAnchor),
            hc.view.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])

        hostingController = hc
    }

    // Walk the responder chain to find the nearest UIViewController.
    private func parentViewController() -> UIViewController? {
        var responder: UIResponder? = self
        while let r = responder {
            if let vc = r as? UIViewController { return vc }
            responder = r.next
        }
        return nil
    }
}

// MARK: - SwiftUI wrapper

/// A SwiftUI view that acts as the DigiaHost root.  An EmptyView is used as
/// content because React Native's own navigation already manages the app's
/// view hierarchy — DigiaHost only needs to be mounted to activate the overlay
/// layer.
private struct DigiaHostWrapperView: View {
    var body: some View {
        DigiaHost {
            EmptyView()
        }
        // No frame constraint here — the view fills its parent (absoluteFill
        // from JS). A non-zero frame is required so UIKit calls viewDidAppear
        // on the UIHostingController, which in turn triggers SwiftUI onAppear
        // and establishes the onChange(activePayload) subscription.
    }
}

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
        #if swift(>=5.9)
            return MainActor.assumeIsolated {
                DigiaHostUIView()
            }
        #else
            return DigiaHostUIView()
        #endif
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
        let parentVC = parentViewController()

        let swiftUIView = DigiaHostWrapperView()
        let hc = UIHostingController(rootView: swiftUIView)
        hc.view.translatesAutoresizingMaskIntoConstraints = false
        hc.view.backgroundColor = .clear

        if let parentVC { parentVC.addChild(hc) }
        // Mount onto self rather than parentVC.view. DigiaHostView is given
        // absoluteFillObject style from JS so self IS full-screen, and SwiftUI
        // will render normally. Mounting here means hitTest below is the single
        // control point for touch dispatch — parentVC.view never has a rogue
        // full-screen sibling that intercepts all RN touches.
        addSubview(hc.view)
        if let parentVC { hc.didMove(toParent: parentVC) }

        NSLayoutConstraint.activate([
            hc.view.leadingAnchor.constraint(equalTo: leadingAnchor),
            hc.view.trailingAnchor.constraint(equalTo: trailingAnchor),
            hc.view.topAnchor.constraint(equalTo: topAnchor),
            hc.view.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])

        hostingController = hc
    }

    // Pass touches through to RN when no overlay is active.
    // When an overlay renders in-host (bottom sheet / dialog inside DigiaHost's ZStack),
    // SwiftUI's hit test returns the overlay view — making the overlay interactive.
    // When nothing is rendered, _UIHostingView.hitTest returns nil or itself; either
    // way we return nil so UIKit falls through to RN content below.
    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        guard let hcView = hostingController?.view else { return nil }
        let hit = hcView.hitTest(point, with: event)
        if hit == nil || hit === hcView { return nil }
        return hit
    }

    /// Prefer React Native’s `UIView.reactViewController`, then walk `next` from each view’s `.next`.
    private func parentViewController() -> UIViewController? {
        let reactSel = NSSelectorFromString("reactViewController")
        var view: UIView? = self
        while let v = view {
            if v.responds(to: reactSel), let raw = v.perform(reactSel)?.takeUnretainedValue() {
                if let vc = raw as? UIViewController { return vc }
            }
            view = v.superview
        }
        view = self
        while let v = view {
            var r: UIResponder? = v.next
            while let responder = r {
                if let vc = responder as? UIViewController { return vc }
                r = responder.next
            }
            view = v.superview
        }
        return nil
    }
}

// MARK: - SwiftUI wrapper

/// A SwiftUI view that acts as the DigiaHost root.  An EmptyView is used as
/// content because React Native's own navigation already manages the app's
/// view hierarchy — DigiaHost only needs to be mounted to activate the overlay
/// layer.
struct DigiaHostWrapperView: View {
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

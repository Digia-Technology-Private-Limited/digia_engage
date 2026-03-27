/**
 * DigiaSlotViewManager
 *
 * React Native ViewManager that exposes a UIView wrapping DigiaSlot (from the
 * Digia iOS SDK) as the native view behind the JS <DigiaSlotView> component.
 *
 * DigiaSlot is a SwiftUI view, so it is bridged into UIKit via a
 * UIHostingController.  The placementKey prop is forwarded to the SwiftUI
 * view via a StateObject/ObservableObject binding.
 *
 * Supported JS props:
 * - `placementKey` (String) — matches the placement key set in the Digia dashboard.
 */
import SwiftUI
import React
import DigiaEngage

@objc(DigiaSlotView)
final class DigiaSlotViewManager: RCTViewManager {

    override static func requiresMainQueueSetup() -> Bool { true }

    override func view() -> UIView! {
        return DigiaSlotUIView()
    }

    // ── prop setter wired by ObjC runtime via RCT_EXPORT_VIEW_PROPERTY ────────
    // The @objc name must match the RCT_EXPORT_VIEW_PROPERTY in the .m file.
    @objc func setPlacementKey(_ placementKey: String, forView view: DigiaSlotUIView) {
        view.placementKey = placementKey
    }
}

// MARK: - DigiaSlotUIView

/// UIView container that embeds DigiaSlot<EmptyView> in a UIHostingController.
/// Re-creates the SwiftUI view when placementKey changes.
final class DigiaSlotUIView: UIView {

    @objc var placementKey: String = "" {
        didSet {
            guard placementKey != oldValue else { return }
            remount()
        }
    }

    private var hostingController: UIHostingController<DigiaSlotWrapperView>?

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window != nil {
            if hostingController == nil { remount() }
        } else {
            teardown()
        }
    }

    private func remount() {
        teardown()
        guard window != nil, !placementKey.isEmpty else { return }
        guard let parentVC = parentViewController() else { return }

        let swiftUIView = DigiaSlotWrapperView(placementKey: placementKey)
        let hc = UIHostingController(rootView: swiftUIView)
        hc.view.translatesAutoresizingMaskIntoConstraints = false
        hc.view.backgroundColor = .clear

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

    private func teardown() {
        hostingController?.willMove(toParent: nil)
        hostingController?.view.removeFromSuperview()
        hostingController?.removeFromParent()
        hostingController = nil
    }

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

private struct DigiaSlotWrapperView: View {
    let placementKey: String

    var body: some View {
        DigiaSlot(placementKey)
    }
}

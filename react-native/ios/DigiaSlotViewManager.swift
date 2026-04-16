import SwiftUI
import React
import DigiaEngage

@objc(DigiaSlotView)
final class DigiaSlotViewManager: RCTViewManager {

    override static func requiresMainQueueSetup() -> Bool { true }

    override func view() -> UIView! {
        return DigiaSlotUIView()
    }

    @objc func setPlacementKey(_ placementKey: String, forView view: DigiaSlotUIView) {
        view.placementKey = placementKey
    }
}

// MARK: - DigiaSlotUIView

/// UIView container that embeds DigiaSlot<EmptyView> in a UIHostingController.
/// Re-creates the SwiftUI view when placementKey changes.
final class DigiaSlotUIView: UIView {

    override init(frame: CGRect) {
        super.init(frame: frame)
        clipsToBounds = false
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        clipsToBounds = false
    }

    @objc var placementKey: String = "" {
        didSet {
            guard placementKey != oldValue else { return }
            lastReportedHeight = -.infinity
            remount()
        }
    }

    /// Fired when SwiftUI slot content intrinsic height changes (parity with Android `onContentSizeChange`).
    @objc var onContentSizeChange: RCTDirectEventBlock?

    private var hostingController: SlotHostingController<DigiaSlotWrapperView>?
    private var lastReportedHeight: CGFloat = -.infinity
    private var delegateProxiesInstalled = false

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window != nil {
            if hostingController == nil { remount() }
            // Deferred so the full RN view hierarchy (including the Fabric
            // surface root with RCTSurfaceTouchHandler) is established.
            DispatchQueue.main.async { [weak self] in
                self?.installDelegateProxiesIfNeeded()
            }
        } else {
            teardown()
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        emitContentSizeIfNeeded()
    }

    // -- Touch handling -------------------------------------------------------
    // RCTSurfaceTouchHandler claims touches before SwiftUI can handle them.
    // RCTTouchDelegateProxy wraps its delegate and blocks touches that land
    // within a registered hosting view, so SwiftUI gestures fire normally.

    /// Expand the tappable area to include the hosting view when it overflows self.bounds.
    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        if super.point(inside: point, with: event) { return true }
        guard let hcView = hostingController?.view else { return false }
        let converted = convert(point, to: hcView)
        return hcView.point(inside: converted, with: event)
    }

    /// Forward hit-testing to SwiftUI view hierarchy even outside self.bounds.
    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        guard let hcView = hostingController?.view, isUserInteractionEnabled, !isHidden, alpha > 0.01 else {
            return super.hitTest(point, with: event)
        }
        let converted = convert(point, to: hcView)
        if let target = hcView.hitTest(converted, with: event) {
            return target
        }
        return super.hitTest(point, with: event)
    }

    /// Walk ancestors to find RCT touch handlers and install the delegate proxy.
    private func installDelegateProxiesIfNeeded() {
        guard !delegateProxiesInstalled, let hcView = hostingController?.view else { return }
        delegateProxiesInstalled = true

        RCTTouchDelegateProxy.registerHostingView(hcView)

        var ancestor: UIView? = superview
        while let v = ancestor {
            for gr in v.gestureRecognizers ?? [] {
                let name = String(describing: type(of: gr))
                if name.hasPrefix("RCT"), name.lowercased().contains("touch") {
                    RCTTouchDelegateProxy.installIfNeeded(on: gr)
                }
            }
            ancestor = v.superview
        }
    }

    private func remount() {
        teardown()
        guard window != nil, !placementKey.isEmpty else { return }
        guard let parentVC = parentViewController() else { return }

        let swiftUIView = DigiaSlotWrapperView(placementKey: placementKey)
        let hc = SlotHostingController(rootView: swiftUIView)
        hc.sizingOptions = .intrinsicContentSize
        hc.view.translatesAutoresizingMaskIntoConstraints = false
        hc.view.backgroundColor = .clear
        hc.onLayoutChange = { [weak self] in
            self?.emitContentSizeIfNeeded()
        }

        parentVC.addChild(hc)
        addSubview(hc.view)
        hc.didMove(toParent: parentVC)

        // No bottom constraint — lets SwiftUI report intrinsic height via onContentSizeChange.
        NSLayoutConstraint.activate([
            hc.view.leadingAnchor.constraint(equalTo: leadingAnchor),
            hc.view.trailingAnchor.constraint(equalTo: trailingAnchor),
            hc.view.topAnchor.constraint(equalTo: topAnchor),
        ])

        hostingController = hc
        DispatchQueue.main.async { [weak self] in
            self?.emitContentSizeIfNeeded()
        }
    }

    private func teardown() {
        if let hcView = hostingController?.view {
            RCTTouchDelegateProxy.unregisterHostingView(hcView)
        }
        delegateProxiesInstalled = false

        hostingController?.willMove(toParent: nil)
        hostingController?.view.removeFromSuperview()
        hostingController?.removeFromParent()
        hostingController = nil
        lastReportedHeight = -.infinity
    }

    /// Emit content height to JS so it can resize the RN wrapper.
    private func emitContentSizeIfNeeded() {
        guard let hc = hostingController, let block = onContentSizeChange else { return }
        let width = bounds.width
        guard width > 0 else { return }

        hc.view.layoutIfNeeded()
        let intrinsic = hc.view.intrinsicContentSize.height
        let height: CGFloat
        if intrinsic.isFinite, intrinsic > 0, intrinsic != UIView.noIntrinsicMetric {
            height = intrinsic
        } else {
            height = hc.view.systemLayoutSizeFitting(
                CGSize(width: width, height: UIView.layoutFittingCompressedSize.height),
                withHorizontalFittingPriority: .required,
                verticalFittingPriority: .fittingSizeLevel
            ).height
        }

        if abs(height - lastReportedHeight) < 0.5 { return }
        lastReportedHeight = height
        block(["height": height as NSNumber])
    }

    /// Walk the view hierarchy to find the nearest UIViewController.
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

private struct DigiaSlotWrapperView: View {
    let placementKey: String

    var body: some View {
        DigiaSlot(placementKey)
    }
}

// MARK: - Hosting controller subclass

/// Overrides `viewDidLayoutSubviews` so that SwiftUI content size changes
/// (e.g. campaign arriving into `InlineCampaignController`) propagate back
/// to the UIKit `DigiaSlotUIView` which reports the new height to RN.
private final class SlotHostingController<Content: View>: UIHostingController<Content> {
    var onLayoutChange: (() -> Void)?

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        onLayoutChange?()
    }
}

// MARK: - RCT touch delegate proxy

private final class RCTTouchDelegateProxy: NSObject, UIGestureRecognizerDelegate {

    private static let hostingViews = NSHashTable<UIView>.weakObjects()
    private static let proxies = NSMapTable<UIGestureRecognizer, RCTTouchDelegateProxy>.weakToStrongObjects()

    static func registerHostingView(_ view: UIView) {
        hostingViews.add(view)
    }

    static func unregisterHostingView(_ view: UIView) {
        hostingViews.remove(view)
    }

    static func installIfNeeded(on gr: UIGestureRecognizer) {
        guard proxies.object(forKey: gr) == nil else { return }
        let proxy = RCTTouchDelegateProxy()
        proxy.originalDelegate = gr.delegate
        gr.delegate = proxy
        proxies.setObject(proxy, forKey: gr)
    }

    weak var originalDelegate: UIGestureRecognizerDelegate?

    func gestureRecognizer(_ gr: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        if let touchView = touch.view {
            for hv in Self.hostingViews.allObjects {
                if touchView === hv || touchView.isDescendant(of: hv) {
                    return false
                }
            }
        }
        return originalDelegate?.gestureRecognizer?(gr, shouldReceive: touch) ?? true
    }

    override func responds(to aSelector: Selector!) -> Bool {
        if super.responds(to: aSelector) { return true }
        return originalDelegate?.responds(to: aSelector) ?? false
    }

    override func forwardingTarget(for aSelector: Selector!) -> Any? {
        if let original = originalDelegate, original.responds(to: aSelector) {
            return original
        }
        return super.forwardingTarget(for: aSelector)
    }
}

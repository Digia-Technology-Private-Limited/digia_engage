/**
 * DigiaAnchorViewManager
 *
 * iOS RCTViewManager that vends DigiaAnchorContainerUIView — a UIView wrapper
 * that automatically tracks its screen-coordinate frame and registers it with
 * AnchorRegistry whenever it lays out or moves to a new window.
 *
 * This mirrors Android's DigiaAnchorViewManager / DigiaAnchorContainerView.
 */

import Foundation
import React
import UIKit
import DigiaEngage

// MARK: - DigiaAnchorContainerUIView

@objc(DigiaAnchorContainerUIView)
final class DigiaAnchorContainerUIView: UIView {

    @objc var anchorKey: String = "" {
        didSet {
            if !oldValue.isEmpty && oldValue != anchorKey {
                Task { @MainActor in AnchorRegistry.shared.unregister(key: oldValue) }
            }
            reportPosition()
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        reportPosition()
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil {
            guard !anchorKey.isEmpty else { return }
            let key = anchorKey
            Task { @MainActor in AnchorRegistry.shared.unregister(key: key) }
        } else {
            reportPosition()
        }
    }

    private func reportPosition() {
        guard !anchorKey.isEmpty, window != nil else { return }
        // convert(bounds, to: nil) gives frame in window coordinates (UIKit points)
        let rectInWindow = convert(bounds, to: nil)
        let key = anchorKey
        Task { @MainActor in
            AnchorRegistry.shared.register(key: key, rect: rectInWindow)
        }
    }
}

// MARK: - DigiaAnchorViewManager

@objc(DigiaAnchorView)
final class DigiaAnchorViewManager: RCTViewManager {

    override static func requiresMainQueueSetup() -> Bool { true }

    override func view() -> UIView! {
        DigiaAnchorContainerUIView()
    }
}

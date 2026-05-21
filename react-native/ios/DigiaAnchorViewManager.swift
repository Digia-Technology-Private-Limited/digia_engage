import React
import UIKit
import DigiaEngage

@objc(DigiaAnchorView)
final class DigiaAnchorViewManager: RCTViewManager {

    override static func requiresMainQueueSetup() -> Bool { true }

    override func view() -> UIView! {
        return DigiaAnchorUIView()
    }

    @objc func setAnchorKey(_ anchorKey: String, forView view: DigiaAnchorUIView) {
        view.anchorKey = anchorKey
    }
}

// MARK: - DigiaAnchorUIView

final class DigiaAnchorUIView: UIView {

    var anchorKey: String = "" {
        didSet {
            guard anchorKey != oldValue else { return }
            if !oldValue.isEmpty {
                AnchorRegistry.shared.unregister(key: oldValue)
            }
            if !anchorKey.isEmpty, window != nil {
                AnchorRegistry.shared.register(key: anchorKey, view: self)
            }
        }
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window != nil, !anchorKey.isEmpty {
            AnchorRegistry.shared.register(key: anchorKey, view: self)
        } else if window == nil, !anchorKey.isEmpty {
            AnchorRegistry.shared.unregister(key: anchorKey)
        }
    }
}

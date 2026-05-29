import React
import UIKit

@objc(DigiaAnchorView)
final class DigiaAnchorViewManager: RCTViewManager {

    override static func requiresMainQueueSetup() -> Bool { true }

    override func view() -> UIView! {
        return DigiaAnchorUIView()
    }
}

// MARK: - DigiaAnchorUIView
final class DigiaAnchorUIView: UIView {}

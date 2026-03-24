import Foundation

private final class DigiaResourceBundleLocator {}

enum DigiaResourceBundle {
    static let module: Bundle = {
        #if SWIFT_PACKAGE
        return Bundle.module
        #else
        let candidates = [
            Bundle(for: DigiaResourceBundleLocator.self),
            Bundle.main,
        ]

        for candidate in candidates {
            for name in ["DigiaEngageResources", "DigiaEngage", "DigiaEngageReactNative"] {
                if let url = candidate.url(forResource: name, withExtension: "bundle"),
                   let bundle = Bundle(url: url) {
                    return bundle
                }
            }
        }

        return Bundle(for: DigiaResourceBundleLocator.self)
        #endif
    }()
}

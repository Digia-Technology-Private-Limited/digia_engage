import Foundation

private final class DigiaResourceBundleLocator {}

enum DigiaResourceBundle {
    static let module: Bundle = {
        let candidates = [
            Bundle(for: DigiaResourceBundleLocator.self),
            Bundle.main,
        ]

        for candidate in candidates {
            for name in ["DigiaEngageResources", "DigiaEngage", "DigiaEngageReactNative", "DigiaEngage_DigiaEngage"] {
                if let url = candidate.url(forResource: name, withExtension: "bundle"),
                   let bundle = Bundle(url: url) {
                    return bundle
                }
            }
        }

        return Bundle(for: DigiaResourceBundleLocator.self)
    }()
}

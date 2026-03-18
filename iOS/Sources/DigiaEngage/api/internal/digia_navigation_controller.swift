import Foundation

@MainActor
final class DigiaNavigationController: ObservableObject {
    @Published private(set) var rootRoute: String?
    @Published private(set) var path: [String] = []

    func setInitialRoute(_ route: String) {
        guard rootRoute == nil else { return }
        rootRoute = route
        path = []
    }

    func replaceStack(with route: String) {
        rootRoute = route
        path = []
    }

    func reset() {
        rootRoute = nil
        path = []
    }

    func updatePath(_ newPath: [String]) {
        path = newPath
    }

    func push(_ route: String) {
        let normalized = NavigationUtil.normalizedRoute(route)
        guard !normalized.isEmpty else { return }
        if currentRoute == normalized {
            return
        }
        if rootRoute == nil {
            rootRoute = normalized
            return
        }
        path.append(normalized)
    }

    func pop() {
        if !path.isEmpty {
            _ = path.removeLast()
        }
    }

    func popUntil(_ matcher: (String) -> Bool) {
        while let last = currentRoute, !matcher(last), !path.isEmpty {
            _ = path.removeLast()
        }
    }

    var currentRoute: String? {
        path.last ?? rootRoute
    }
}

import SwiftUI

@MainActor
public struct DigiaInitialRouteScreen: View {
    @ObservedObject private var store = DigiaRuntime.shared.appConfigStore
    @ObservedObject private var navigation = DigiaRuntime.shared.navigationController

    public init() {}

    public var body: some View {
        Group {
            if let initialRoute = store.appConfig?.initialRoute {
                NavigationStack(path: Binding(get: { navigation.path }, set: { navigation.updatePath($0) })) {
                    DUIFactory.shared.createPage(navigation.rootRoute?.isEmpty == false ? navigation.rootRoute! : initialRoute)
                        .navigationDestination(for: String.self) { route in
                            DUIFactory.shared.createPage(route)
                        }
                }
                .digiaHideHostNavigationBar()
                .onAppear {
                    DigiaRuntime.shared.navigationController.setInitialRoute(initialRoute)
                }
            } else if let error = store.lastError {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Digia load failed")
                        .font(.headline)
                    Text(error)
                        .font(.caption)
                        .foregroundStyle(.red)
                }
            } else {
                VStack(alignment: .leading, spacing: 12) {
                    ProgressView("Loading Digia App")
                    Text(store.isLoading ? "Waiting for remote AppConfig..." : "No AppConfig loaded yet")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }
}

private extension View {
    @ViewBuilder
    func digiaHideHostNavigationBar() -> some View {
#if os(iOS)
        self
            .navigationBarBackButtonHidden(true)
            .toolbar(.hidden, for: .navigationBar)
            .digiaKeepSwipeBackGestureEnabled()
#else
        self
#endif
    }
}

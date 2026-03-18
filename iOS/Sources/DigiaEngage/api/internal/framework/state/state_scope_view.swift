import SwiftUI

@MainActor
struct StateScopeView: View {
    @ObservedObject private var store: LocalStateStore
    private let content: (LocalStateStore) -> AnyView

    init(store: LocalStateStore, content: @escaping (LocalStateStore) -> AnyView) {
        self.store = store
        self.content = content
    }

    var body: some View {
        content(store)
    }
}

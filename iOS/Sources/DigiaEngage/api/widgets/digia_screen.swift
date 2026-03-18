import SwiftUI

@MainActor
public struct DigiaScreen: View {
    private let name: String

    public init(_ name: String) {
        self.name = name
    }

    public var body: some View {
        DUIFactory.shared.createPage(name)
        .onAppear {
            Digia.setCurrentScreen(name)
        }
    }
}

@MainActor
public extension View {
    func digiaScreen(_ name: String) -> some View {
        onAppear {
            Digia.setCurrentScreen(name)
        }
    }
}

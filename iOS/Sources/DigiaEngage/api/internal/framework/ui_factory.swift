import SwiftUI

@MainActor
final class DUIFactory {
    static let shared = DUIFactory()

    private let widgetRegistry: VirtualWidgetRegistry = DefaultVirtualWidgetRegistry()

    private init() {}

    func createPage(_ id: String, pageArgs: [String: ScopeValue] = [:]) -> AnyView {
        guard let page = DigiaRuntime.shared.appConfigStore.page(id),
              let root = page.layout?.renderRoot else {
            return AnyView(EmptyView())
        }

        return AnyView(
            DUIPageView(
                pageID: id,
                page: page,
                root: root,
                registry: widgetRegistry,
                pageArgs: pageArgs
            )
        )
    }

    func createComponent(
        _ id: String,
        args: [String: ScopeValue] = [:],
        parentStore: LocalStateStore? = nil,
        parentHierarchy: [String] = []
    ) -> AnyView {
        guard let component = DigiaRuntime.shared.appConfigStore.component(id),
              let root = component.layout?.renderRoot else {
            return AnyView(EmptyView())
        }

        return AnyView(
            DUIComponentView(
                componentID: id,
                component: component,
                root: root,
                registry: widgetRegistry,
                args: args,
                parentStore: parentStore,
                parentHierarchy: parentHierarchy
            )
        )
    }
}

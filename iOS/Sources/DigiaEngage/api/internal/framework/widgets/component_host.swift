import SwiftUI

@MainActor
final class VWComponentHost: VirtualLeafStatelessWidget<Void> {
    let componentId: String
    let args: [String: ScopeValue]
    let registry: VirtualWidgetRegistry

    init(
        componentId: String,
        args: [String: ScopeValue],
        commonProps: CommonProps?,
        parentProps: ParentProps?,
        parent: VirtualWidget?,
        refName: String?,
        registry: VirtualWidgetRegistry
    ) {
        self.componentId = componentId
        self.args = args
        self.registry = registry
        super.init(
            props: (),
            commonProps: commonProps,
            parentProps: parentProps,
            parent: parent,
            refName: refName
        )
    }

    override func render(_ payload: RenderPayload) -> AnyView {
        // Prevent component self-recursion (direct or indirect) from causing stack overflows.
        if payload.widgetHierarchy.contains(componentId) {
            #if DEBUG
            NSLog(
                "[DigiaEngage] Recursive component reference blocked for id=%@ hierarchy=%@",
                componentId,
                payload.widgetHierarchy.joined(separator: " -> ")
            )
            #endif
            return empty()
        }
        return DUIFactory.shared.createComponent(
            componentId,
            args: args,
            parentStore: payload.localStateStore,
            parentHierarchy: payload.widgetHierarchy
        )
    }
}

import SwiftUI

@MainActor
final class VWStateContainer: VirtualStatelessWidget<Void> {
    let initStateDefs: [String: ScopeValue]

    init(
        initStateDefs: [String: ScopeValue],
        parentProps: ParentProps?,
        childGroups: [String: [VirtualWidget]]?,
        parent: VirtualWidget?,
        refName: String?
    ) {
        self.initStateDefs = initStateDefs
        super.init(
            props: (),
            commonProps: nil,
            parentProps: parentProps,
            childGroups: childGroups,
            parent: parent,
            refName: refName
        )
    }

    override func render(_ payload: RenderPayload) -> AnyView {
        guard let childWidget = child ?? childGroups?.values.first?.first else {
            return empty()
        }
        let initialState = initStateDefs.mapValues { ScopeValueResolver.resolve($0, in: payload.scopeContext) }
        let store = LocalStateStore(namespace: refName, initialState: initialState, parent: payload.localStateStore)
        return AnyView(
            StateScopeView(store: store) { currentStore in
                let context = LocalStateExprContext(stateStore: currentStore, enclosing: payload.scopeContext)
                let scopedPayload = payload.copyWithChainedContext(context).copyWith(localStateStore: currentStore)
                let view = childWidget.toWidget(scopedPayload)
                    .onAppear { DigiaRuntime.shared.registerLocalStateStore(currentStore) }
                    .onDisappear { DigiaRuntime.shared.unregisterLocalStateStore(currentStore) }
                return AnyView(view)
            }
        )
    }
}

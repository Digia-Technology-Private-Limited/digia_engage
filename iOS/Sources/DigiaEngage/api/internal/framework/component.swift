import SwiftUI

@MainActor
struct DUIComponentView: View {
    let componentID: String
    let component: ComponentDefinition
    let root: VWData
    let registry: VirtualWidgetRegistry
    let args: [String: JSONValue]
    let parentStore: StateContext?

    @ObservedObject private var runtime = SDKInstance.shared
    @StateObject private var stateStore: StateContext

    init(
        componentID: String,
        component: ComponentDefinition,
        root: VWData,
        registry: VirtualWidgetRegistry,
        args: [String: JSONValue],
        parentStore: StateContext?
    ) {
        self.componentID = componentID
        self.component = component
        self.root = root
        self.registry = registry
        self.args = args
        self.parentStore = parentStore
        let initialState = component.initStateDefs?.mapValues { $0.resolvedValue(in: nil) } ?? [:]
        _stateStore = StateObject(wrappedValue: StateContext(namespace: component.uid ?? componentID, initialState: initialState, parent: parentStore))
    }

    var body: some View {
        let baseContext = StateScopeContext(
            stateContext: stateStore,
            variables: args.mapValues(\.anyValue),
            enclosing: AppStateExprContext(
                values: runtime.appState.mapValues(\.anyValue),
                streams: runtime.appStateStreams.mapValues { $0 as Any }
            )
        )
        let payload = RenderPayload(
            resources: ResourceProvider(fontFactory: runtime.fontFactory, appConfigStore: runtime.appConfigStore),
            scopeContext: baseContext,
            localStateStore: stateStore
        )
        let widget = try? registry.createWidget(root, parent: nil)
        return (widget?.toWidget(payload) ?? AnyView(EmptyView()))
            .onAppear { SDKInstance.shared.registerStateContext(self.stateStore) }
            .onDisappear { SDKInstance.shared.unregisterStateContext(self.stateStore) }
    }
}

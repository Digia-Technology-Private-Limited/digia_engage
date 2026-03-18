import DigiaExpr
import SwiftUI

@MainActor
struct DUIPageView: View {
    let pageID: String
    let page: PageDefinition
    let root: VWData
    let registry: VirtualWidgetRegistry
    let pageArgs: [String: ScopeValue]

    @ObservedObject private var runtime = DigiaRuntime.shared
    @StateObject private var stateStore: LocalStateStore
    @State private var didRunPageLoad = false

    init(
        pageID: String,
        page: PageDefinition,
        root: VWData,
        registry: VirtualWidgetRegistry,
        pageArgs: [String: ScopeValue] = [:]
    ) {
        self.pageID = pageID
        self.page = page
        self.root = root
        self.registry = registry
        self.pageArgs = pageArgs
        let initialState = page.initStateDefs?.mapValues { $0.resolvedValue(in: nil) } ?? [:]
        _stateStore = StateObject(wrappedValue: LocalStateStore(namespace: page.uid ?? pageID, initialState: initialState))
    }

    var body: some View {
        StateScopeView(store: stateStore) { store in
            let baseContext = LocalStateExprContext(
                stateStore: store,
                variables: pageArgs.mapValues(\.anyValue),
                enclosing: AppStateExprContext(
                    values: runtime.appState.mapValues(\.anyValue),
                    streams: runtime.appStateStreams.mapValues { $0 as Any }
                )
            )
            let payload = RenderPayload(
                appConfigStore: runtime.appConfigStore,
                scopeContext: baseContext,
                widgetHierarchy: [],
                currentEntityId: page.uid ?? pageID,
                localStateStore: store
            )
            let widget = try? registry.createWidget(root, parent: nil)
            let view = (widget?.toWidget(payload) ?? AnyView(EmptyView()))
                .onAppear {
                    DigiaRuntime.shared.registerLocalStateStore(store)
                    Digia.setCurrentScreen(pageID)
                    if !didRunPageLoad {
                        didRunPageLoad = true
                        payload.executeAction(page.actions?.onPageLoadAction, triggerType: "onPageLoad")
                    }
                }
                .onDisappear {
                    DigiaRuntime.shared.unregisterLocalStateStore(store)
                }
                .digiaHideBackButton()
            return AnyView(view)
        }
    }
}

@MainActor
struct DUIComponentView: View {
    let componentID: String
    let component: ComponentDefinition
    let root: VWData
    let registry: VirtualWidgetRegistry
    let args: [String: ScopeValue]
    let parentStore: LocalStateStore?
    let parentHierarchy: [String]

    @ObservedObject private var runtime = DigiaRuntime.shared
    @StateObject private var stateStore: LocalStateStore

    init(
        componentID: String,
        component: ComponentDefinition,
        root: VWData,
        registry: VirtualWidgetRegistry,
        args: [String: ScopeValue],
        parentStore: LocalStateStore?,
        parentHierarchy: [String]
    ) {
        self.componentID = componentID
        self.component = component
        self.root = root
        self.registry = registry
        self.args = args
        self.parentStore = parentStore
        self.parentHierarchy = parentHierarchy
        let initialState = component.initStateDefs?.mapValues { $0.resolvedValue(in: nil) } ?? [:]
        _stateStore = StateObject(wrappedValue: LocalStateStore(namespace: component.uid ?? componentID, initialState: initialState, parent: parentStore))
    }

    var body: some View {
        StateScopeView(store: stateStore) { store in
            let baseContext = LocalStateExprContext(
                stateStore: store,
                variables: args.mapValues(\.anyValue),
                enclosing: AppStateExprContext(
                    values: runtime.appState.mapValues(\.anyValue),
                    streams: runtime.appStateStreams.mapValues { $0 as Any }
                )
            )
            let payload = RenderPayload(
                appConfigStore: runtime.appConfigStore,
                scopeContext: baseContext,
                widgetHierarchy: parentHierarchy + [componentID],
                currentEntityId: componentID,
                localStateStore: store
            )
            let widget = try? registry.createWidget(root, parent: nil)
            let view = (widget?.toWidget(payload) ?? AnyView(EmptyView()))
                .onAppear { DigiaRuntime.shared.registerLocalStateStore(store) }
                .onDisappear { DigiaRuntime.shared.unregisterLocalStateStore(store) }
            return AnyView(view)
        }
    }
}

private extension View {
    @ViewBuilder
    func digiaHideBackButton() -> some View {
#if os(iOS)
        self
            .navigationBarBackButtonHidden(true)
            .digiaKeepSwipeBackGestureEnabled()
#else
        self
#endif
    }
}

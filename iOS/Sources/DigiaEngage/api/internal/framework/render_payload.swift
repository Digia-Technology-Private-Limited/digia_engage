import DigiaExpr
import SwiftUI

@MainActor
struct RenderPayload {
    let appConfigStore: AppConfigStore
    let scopeContext: any ExprContext
    let widgetHierarchy: [String]
    let currentEntityId: String?
    let actionExecutor: ActionExecutor
    let localStateStore: LocalStateStore?

    init(
        appConfigStore: AppConfigStore,
        scopeContext: (any ExprContext)? = nil,
        widgetHierarchy: [String] = [],
        currentEntityId: String? = nil,
        actionExecutor: ActionExecutor = ActionExecutor(),
        localStateStore: LocalStateStore? = nil
    ) {
        self.appConfigStore = appConfigStore
        let rootContext = scopeContext ?? AppStateExprContext(
            values: DigiaRuntime.shared.appState.mapValues(\.anyValue),
            streams: DigiaRuntime.shared.appStateStreams.mapValues { $0 as Any }
        )
        self.scopeContext = rootContext
        self.widgetHierarchy = widgetHierarchy
        self.currentEntityId = currentEntityId
        self.actionExecutor = actionExecutor
        self.localStateStore = localStateStore
    }

    func resolveColor(_ value: String?) -> Color? {
        guard let value else { return nil }
        if let color = Color(hex: value) {
            return color
        }
        return appConfigStore.themeColor(named: value).flatMap(Color.init(hex:))
    }

    func eval(_ value: ExprOr<String>?, scopeContext incoming: (any ExprContext)? = nil) -> String? {
        value?.resolve(in: chainContext(incoming))
    }

    func eval(_ value: ExprOr<Bool>?, scopeContext incoming: (any ExprContext)? = nil) -> Bool? {
        value?.resolve(in: chainContext(incoming))
    }

    func eval(_ value: ExprOr<Int>?, scopeContext incoming: (any ExprContext)? = nil) -> Int? {
        value?.resolve(in: chainContext(incoming))
    }

    func eval(_ value: ExprOr<Double>?, scopeContext incoming: (any ExprContext)? = nil) -> Double? {
        value?.resolve(in: chainContext(incoming))
    }

    func evalScopeValue(_ value: ScopeValue?, scopeContext incoming: (any ExprContext)? = nil) -> ScopeValue? {
        guard let value else { return nil }
        return ScopeValueResolver.resolve(value, in: chainContext(incoming))
    }

    func evalAny(_ value: ScopeValue?, scopeContext incoming: (any ExprContext)? = nil) -> Any? {
        ScopeValueResolver.resolveAny(value, in: chainContext(incoming))
    }

    func evalColor(_ value: ExprOr<String>?, scopeContext incoming: (any ExprContext)? = nil) -> Color? {
        resolveColor(eval(value, scopeContext: incoming))
    }

    func executeAction(
        _ actionFlow: ActionFlow?,
        triggerType: String? = nil,
        scopeContext overrideContext: (any ExprContext)? = nil
    ) {
        actionExecutor.execute(
            actionFlow,
            appConfig: appConfigStore,
            scopeContext: overrideContext ?? scopeContext,
            triggerType: triggerType,
            widgetHierarchy: widgetHierarchy,
            currentEntityId: currentEntityId,
            localStateStore: localStateStore
        )
    }

    func withExtendedHierarchy(_ widgetName: String) -> RenderPayload {
        copyWith(widgetHierarchy: widgetHierarchy + [widgetName])
    }

    func forComponent(componentId: String) -> RenderPayload {
        copyWith(widgetHierarchy: widgetHierarchy + [componentId], currentEntityId: componentId)
    }

    func copyWithChainedContext(_ context: any ExprContext) -> RenderPayload {
        copyWith(scopeContext: chainContext(context))
    }

    func copyWith(
        scopeContext: (any ExprContext)? = nil,
        widgetHierarchy: [String]? = nil,
        currentEntityId: String? = nil,
        localStateStore: LocalStateStore? = nil
    ) -> RenderPayload {
        RenderPayload(
            appConfigStore: appConfigStore,
            scopeContext: scopeContext ?? self.scopeContext,
            widgetHierarchy: widgetHierarchy ?? self.widgetHierarchy,
            currentEntityId: currentEntityId ?? self.currentEntityId,
            actionExecutor: actionExecutor,
            localStateStore: localStateStore ?? self.localStateStore
        )
    }

    private func chainContext(_ incoming: (any ExprContext)?) -> any ExprContext {
        guard let incoming else { return scopeContext }
        incoming.addContextAtTail(scopeContext)
        return incoming
    }
}

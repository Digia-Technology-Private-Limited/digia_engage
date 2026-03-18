import DigiaExpr
import Foundation

struct ActionProcessorContext {
    let appConfig: AppConfigStore
    let widgetHierarchy: [String]
    let currentEntityId: String?
    let scopeContext: (any ExprContext)?
    let localStateStore: LocalStateStore?
    let actionExecutor: ActionExecutor

    init(
        appConfig: AppConfigStore,
        widgetHierarchy: [String],
        currentEntityId: String?,
        scopeContext: (any ExprContext)? = nil,
        localStateStore: LocalStateStore? = nil,
        actionExecutor: ActionExecutor = ActionExecutor()
    ) {
        self.appConfig = appConfig
        self.widgetHierarchy = widgetHierarchy
        self.currentEntityId = currentEntityId
        self.scopeContext = scopeContext
        self.localStateStore = localStateStore
        self.actionExecutor = actionExecutor
    }
}

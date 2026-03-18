import DigiaExpr
import Foundation

final class LocalStateExprContext: ExprContext {
    let name: String
    var enclosing: (any ExprContext)?

    private let stateStore: LocalStateStore
    private let variables: [String: Any?]

    init(
        stateStore: LocalStateStore,
        variables: [String: Any?] = [:],
        enclosing: (any ExprContext)? = nil
    ) {
        self.stateStore = stateStore
        self.variables = variables
        self.enclosing = enclosing
        name = stateStore.namespace ?? ""
    }

    func getValue(_ key: String) -> ExprLookupResult {
        if let value = variables[key] {
            return ExprLookupResult(found: true, value: ExprValue.from(value))
        }

        if key == "state" || (!name.isEmpty && key == name) {
            return ExprLookupResult(found: true, value: .map(stateStore.stateVariables.mapValues { ExprValue.from($0.anyValue) }))
        }

        if let value = stateStore.getValue(key) {
            return ExprLookupResult(found: true, value: ExprValue.from(value.anyValue))
        }

        return enclosing?.getValue(key) ?? ExprLookupResult(found: false, value: nil)
    }
}

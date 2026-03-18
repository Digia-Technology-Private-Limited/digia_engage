import DigiaExpr
import Foundation

enum ScopeValueResolver {
    static func resolve(_ value: ScopeValue, in context: (any ExprContext)?) -> ScopeValue {
        switch value {
        case let .string(raw):
            guard Expression.hasExpression(raw) || Expression.isExpression(raw) else {
                return .string(raw)
            }
            do {
                let resolved = try DigiaExpr.Expression.eval(raw, context)
                return scopeValue(from: resolved)
            } catch {
                return .string(raw)
            }
        case let .array(items):
            return .array(items.map { resolve($0, in: context) })
        case let .object(items):
            return .object(items.mapValues { resolve($0, in: context) })
        default:
            return value
        }
    }

    static func resolveAny(_ value: ScopeValue?, in context: (any ExprContext)?) -> Any? {
        guard let value else { return nil }
        switch value {
        case let .string(raw):
            guard Expression.hasExpression(raw) || Expression.isExpression(raw) else {
                return raw
            }
            return try? DigiaExpr.Expression.eval(raw, context)
        default:
            return resolve(value, in: context).anyValue
        }
    }

    static func scopeValue(from value: Any?) -> ScopeValue {
        switch value {
        case let value as ScopeValue:
            return value
        case let value as String:
            return .string(value)
        case let value as Int:
            return .int(value)
        case let value as Int64:
            return .int(Int(value))
        case let value as Int32:
            return .int(Int(value))
        case let value as Double:
            return .double(value)
        case let value as Float:
            return .double(Double(value))
        case let value as Bool:
            return .bool(value)
        case let value as NSNumber:
            let doubleValue = value.doubleValue
            if floor(doubleValue) == doubleValue {
                return .int(value.intValue)
            }
            return .double(doubleValue)
        case let value as [Any?]:
            return .array(value.map(scopeValue(from:)))
        case let value as [String: Any?]:
            return .object(value.mapValues { scopeValue(from: $0) })
        case nil:
            return .null
        default:
            return .string(String(describing: value))
        }
    }
}

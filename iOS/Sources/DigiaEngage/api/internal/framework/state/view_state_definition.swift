import DigiaExpr
import Foundation

struct ViewStateDefinition: Decodable, Equatable, Sendable {
    let type: String
    let defaultValue: ScopeValue?

    enum CodingKeys: String, CodingKey {
        case type
        case `default`
        case defaultValue
    }

    init(type: String, defaultValue: ScopeValue?) {
        self.type = type
        self.defaultValue = defaultValue
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        type = try container.decode(String.self, forKey: .type)
        defaultValue = try container.decodeIfPresent(ScopeValue.self, forKey: .default)
            ?? container.decodeIfPresent(ScopeValue.self, forKey: .defaultValue)
    }

    func resolvedValue(in context: (any ExprContext)?) -> ScopeValue {
        let fallback = defaultValue ?? defaultValueForType(type)
        return ScopeValueResolver.resolve(fallback, in: context)
    }

    private func defaultValueForType(_ rawType: String) -> ScopeValue {
        switch rawType.lowercased() {
        case "number", "numeric":
            return .int(0)
        case "bool", "boolean":
            return .bool(false)
        case "json":
            return .object([:])
        case "list", "array":
            return .array([])
        default:
            return .string("")
        }
    }
}

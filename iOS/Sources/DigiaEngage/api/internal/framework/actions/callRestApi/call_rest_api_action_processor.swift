import DigiaExpr
import Foundation

struct CallRestApiAction: Sendable {
    let actionType: ActionType = .callRestApi
    let disableActionIf: ExprOr<Bool>?
    let data: [String: ScopeValue]
}

@MainActor
struct CallRestApiProcessor {
    let processorType: ActionType = .callRestApi

    func execute(action: CallRestApiAction, context: ActionProcessorContext) async throws {
        guard let dataSource = action.data.object("dataSource"),
              let dataSourceID = dataSource.string("id"),
              let apiModel = context.appConfig.appConfig?.rest.resource(dataSourceID) else {
            throw ActionExecutionError.unsupportedContext(processorType)
        }

        let resolvedArgs = resolveArguments(apiModel: apiModel, dataSource: dataSource, context: context)
        let request = try makeRequest(apiModel: apiModel, args: resolvedArgs, baseURL: context.appConfig.appConfig?.rest.baseUrl)
        do {
            let (data, response): (Data, URLResponse?)
            if request.url?.isFileURL == true, let url = request.url {
                data = try Data(contentsOf: url)
                response = nil
            } else {
                let network = try await URLSession.shared.data(for: request)
                data = network.0
                response = network.1
            }
            let http = response as? HTTPURLResponse
            let responseObject = buildResponseObject(data: data, response: http, request: request, error: nil)
            let successContext = BasicExprContext(variables: ["response": responseObject.mapValues(\.anyValue)])
            if let scopeContext = context.scopeContext {
                successContext.addContextAtTail(scopeContext)
            }
            let isSuccess = evaluateSuccessCondition(action.data["successCondition"], scopeContext: successContext)
            let nextFlow = isSuccess ? action.data["onSuccess"]?.asActionFlow() : action.data["onError"]?.asActionFlow()
            await context.actionExecutor.executeNow(
                nextFlow,
                appConfig: context.appConfig,
                scopeContext: successContext,
                triggerType: isSuccess ? "onSuccess" : "onError",
                widgetHierarchy: context.widgetHierarchy,
                currentEntityId: context.currentEntityId,
                localStateStore: context.localStateStore
            )
        } catch {
            let responseObject = buildResponseObject(data: nil, response: nil, request: request, error: error)
            let errorContext = BasicExprContext(variables: ["response": responseObject.mapValues(\.anyValue)])
            if let scopeContext = context.scopeContext {
                errorContext.addContextAtTail(scopeContext)
            }
            await context.actionExecutor.executeNow(
                action.data["onError"]?.asActionFlow(),
                appConfig: context.appConfig,
                scopeContext: errorContext,
                triggerType: "onError",
                widgetHierarchy: context.widgetHierarchy,
                currentEntityId: context.currentEntityId,
                localStateStore: context.localStateStore
            )
            throw error
        }
    }

    private func evaluateSuccessCondition(_ value: ScopeValue?, scopeContext: any ExprContext) -> Bool {
        guard case let .string(expression)? = value else { return true }
        guard (Expression.hasExpression(expression) || Expression.isExpression(expression)) else { return true }
        return (try? DigiaExpr.Expression.eval(expression, scopeContext) as? Bool) ?? true
    }

    private func resolveArguments(apiModel: APIModel, dataSource: [String: ScopeValue], context: ActionProcessorContext) -> [String: ScopeValue] {
        let configured = apiModel.variables?.mapValues { $0.resolvedValue(in: context.scopeContext) } ?? [:]
        let inline = dataSource.object("args")?.mapValues { ScopeValueResolver.resolve($0, in: context.scopeContext) } ?? [:]
        return configured.merging(inline) { _, rhs in rhs }
    }

    private func makeRequest(apiModel: APIModel, args: [String: ScopeValue], baseURL: String?) throws -> URLRequest {
        let hydratedURL = hydrateTemplate(apiModel.url, args: args)
        let urlString = hydratedURL.hasPrefix("http") ? hydratedURL : (baseURL ?? "") + hydratedURL
        guard let url = URL(string: urlString) else {
            throw ActionExecutionError.unsupportedContext(processorType)
        }
        var request = URLRequest(url: url)
        request.httpMethod = apiModel.method.uppercased()
        var headers = apiModel.headers?.reduce(into: [String: String]()) { partialResult, entry in
            partialResult[entry.key] = stringValue(from: hydrateScopeValue(entry.value, args: args))
        } ?? [:]
        headers["Content-Type", default: "application/json"] = "application/json"
        request.allHTTPHeaderFields = headers
        if apiModel.method.lowercased() != "get", let body = apiModel.body {
            let hydratedBody = hydrateScopeValue(body, args: args)
            request.httpBody = try JSONSerialization.data(withJSONObject: hydratedBody.anyValue ?? [:])
        }
        return request
    }

    private func hydrateScopeValue(_ value: ScopeValue, args: [String: ScopeValue]) -> ScopeValue {
        switch value {
        case let .string(string):
            if string.contains("{{") {
                return .string(hydrateTemplate(string, args: args))
            }
            return value
        case let .array(values):
            return .array(values.map { hydrateScopeValue($0, args: args) })
        case let .object(values):
            return .object(values.mapValues { hydrateScopeValue($0, args: args) })
        default:
            return value
        }
    }

    private func hydrateTemplate(_ template: String, args: [String: ScopeValue]) -> String {
        let regex = try? NSRegularExpression(pattern: #"\{\{([\w\.\-]+)\}\}"#)
        let range = NSRange(location: 0, length: template.utf16.count)
        let matches = regex?.matches(in: template, range: range) ?? []
        var result = template
        for match in matches.reversed() {
            guard let fullRange = Range(match.range(at: 0), in: result),
                  let keyRange = Range(match.range(at: 1), in: result) else { continue }
            let key = String(result[keyRange])
            let value = stringValue(from: args[key] ?? .null)
            result.replaceSubrange(fullRange, with: value)
        }
        return result
    }

    private func stringValue(from value: ScopeValue) -> String {
        switch value {
        case let .string(value): return value
        case let .int(value): return String(value)
        case let .double(value): return String(value)
        case let .bool(value): return String(value)
        case let .array(value): return String(describing: value.map(\.anyValue))
        case let .object(value): return String(describing: value.mapValues(\.anyValue))
        case .null: return ""
        }
    }

    private func buildResponseObject(data: Data?, response: HTTPURLResponse?, request: URLRequest, error: Error?) -> [String: ScopeValue] {
        let bodyObject: ScopeValue
        if let data,
           let json = try? JSONSerialization.jsonObject(with: data) {
            bodyObject = ScopeValueResolver.scopeValue(from: json)
        } else if let data, let string = String(data: data, encoding: .utf8) {
            bodyObject = .string(string)
        } else {
            bodyObject = .null
        }
        return [
            "body": bodyObject,
            "statusCode": response.map { .int($0.statusCode) } ?? .null,
            "headers": ScopeValueResolver.scopeValue(from: response?.allHeaderFields as? [String: Any]),
            "requestObj": .object([
                "url": .string(request.url?.absoluteString ?? ""),
                "method": .string(request.httpMethod ?? "GET"),
            ]),
            "error": error.map { .string(String(describing: $0)) } ?? .null,
        ]
    }
}

private extension ScopeValue {
    func asActionFlow() -> ActionFlow? {
        guard case let .object(object) = self,
              case let .array(steps)? = object["steps"] else { return nil }
        let decodedSteps = steps.compactMap { item -> ActionStep? in
            guard case let .object(obj) = item,
                  let type = obj.string("type") else { return nil }
            return ActionStep(type: type, data: obj.object("data"), disableActionIf: nil)
        }
        return ActionFlow(steps: decodedSteps)
    }
}

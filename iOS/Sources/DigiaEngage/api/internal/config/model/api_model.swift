import Foundation

struct APIModel: Decodable, Equatable, Sendable {
    let id: String
    let name: String?
    let url: String
    let method: String
    let headers: [String: ScopeValue]?
    let body: ScopeValue?
    let bodyType: String?
    let variables: [String: ViewStateDefinition]?

    init(
        id: String,
        name: String?,
        url: String,
        method: String,
        headers: [String: ScopeValue]?,
        body: ScopeValue?,
        bodyType: String?,
        variables: [String: ViewStateDefinition]?
    ) {
        self.id = id
        self.name = name
        self.url = url
        self.method = method
        self.headers = headers
        self.body = body
        self.bodyType = bodyType
        self.variables = variables
    }

    private enum CodingKeys: String, CodingKey {
        case id
        case name
        case url
        case method
        case headers
        case body
        case bodyType
        case variables
    }
}

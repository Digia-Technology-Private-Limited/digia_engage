import Foundation

public struct InAppPayload: Sendable, Codable, Equatable {
    public let id: String
    public let content: InAppPayloadContent
    public let cepContext: [String: String]

    public init(
        id: String,
        content: InAppPayloadContent,
        cepContext: [String: String] = [:]
    ) {
        self.id = id
        self.content = content
        self.cepContext = cepContext
    }
}

public struct InAppPayloadContent: Sendable, Codable, Equatable {
    public let type: String
    public let placementId: String?
    public let title: String?
    public let text: String?

    public init(
        type: String,
        placementId: String? = nil,
        title: String? = nil,
        text: String? = nil
    ) {
        self.type = type
        self.placementId = placementId
        self.title = title
        self.text = text
    }
}

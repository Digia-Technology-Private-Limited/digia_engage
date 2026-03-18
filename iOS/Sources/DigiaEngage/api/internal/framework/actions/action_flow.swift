import Foundation

struct ActionFlow: Codable, Equatable, Sendable {
    let steps: [ActionStep]
    let inkwell: Bool
    let analyticsData: [AnalyticsDatum]

    init(steps: [ActionStep] = [], inkwell: Bool = true, analyticsData: [AnalyticsDatum] = []) {
        self.steps = steps
        self.inkwell = inkwell
        self.analyticsData = analyticsData
    }

    var isEmpty: Bool {
        steps.isEmpty && analyticsData.isEmpty
    }

    private enum CodingKeys: String, CodingKey {
        case steps
        case inkwell = "inkWell"
        case analyticsData
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        steps = try container.decodeIfPresent([ActionStep].self, forKey: .steps) ?? []
        inkwell = try container.decodeIfPresent(Bool.self, forKey: .inkwell) ?? true
        analyticsData = try container.decodeIfPresent([AnalyticsDatum].self, forKey: .analyticsData) ?? []
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(steps, forKey: .steps)
        try container.encode(inkwell, forKey: .inkwell)
        try container.encode(analyticsData, forKey: .analyticsData)
    }
}

struct ActionStep: Codable, Equatable, Sendable {
    let type: String
    let data: [String: ScopeValue]?
    let disableActionIf: ExprOr<Bool>?

    init(type: String, data: [String: ScopeValue]? = nil, disableActionIf: ExprOr<Bool>? = nil) {
        self.type = type
        self.data = data
        self.disableActionIf = disableActionIf
    }
}

struct AnalyticsDatum: Codable, Equatable, Sendable {
    let key: String?
    let value: String?
}

import Foundation

struct FlexProps: Codable, Equatable, Sendable {
    let spacing: Double?
    let startSpacing: Double?
    let endSpacing: Double?
    let mainAxisAlignment: String?
    let crossAxisAlignment: String?
    let mainAxisSize: String?
    let isScrollable: Bool?
    let dataSource: ScopeValue?
}

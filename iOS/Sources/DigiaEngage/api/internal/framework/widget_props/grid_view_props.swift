import Foundation

struct GridViewProps: Codable, Equatable, Sendable {
    let controller: ScopeValue?
    let allowScroll: Bool?
    let shrinkWrap: Bool?
    let mainAxisSpacing: Double?
    let crossAxisSpacing: Double?
    let scrollDirection: String?
    let crossAxisCount: Int?
    let dataSource: ScopeValue?
}

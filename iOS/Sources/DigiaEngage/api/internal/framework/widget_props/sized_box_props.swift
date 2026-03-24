import Foundation

struct SizedBoxProps: Codable, Equatable, Sendable {
    let width: ExprOr<Double>?
    let height: ExprOr<Double>?
}

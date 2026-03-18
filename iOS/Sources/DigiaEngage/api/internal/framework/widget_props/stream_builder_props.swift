import Foundation

struct StreamBuilderProps: Codable, Equatable, Sendable {
    let controller: ScopeValue?
    let initialData: ScopeValue?
    let onSuccess: ActionFlow?
    let onError: ActionFlow?
}

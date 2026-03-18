import Foundation

struct VideoPlayerProps: Decodable, Equatable, Sendable {
    let videoURL: ScopeValue?
    let showControls: ExprOr<Bool>?
    let aspectRatio: ExprOr<Double>?
    let autoPlay: ExprOr<Bool>?
    let looping: ExprOr<Bool>?

    private enum CodingKeys: String, CodingKey {
        case videoURL = "videoUrl"
        case showControls
        case aspectRatio
        case autoPlay
        case looping
    }
}

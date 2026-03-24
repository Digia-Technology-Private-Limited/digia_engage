import Foundation

struct ContainerProps: Decodable, Equatable, Sendable {
    let color: ExprOr<String>?
    let padding: Spacing?
    let margin: Spacing?
    let width: ExprOr<Double>?
    let height: ExprOr<Double>?
    let minWidth: ExprOr<Double>?
    let minHeight: ExprOr<Double>?
    let maxWidth: ExprOr<Double>?
    let maxHeight: ExprOr<Double>?
    let childAlignment: String?
    let borderRadius: CornerRadiusProps?
    let border: BorderStyle?
    let shape: String?
    let elevation: Double?
    let decorationImage: DecorationImageProps?
    let shadow: [ShadowStyle]?
    let gradiant: GradientStyle?
}

struct DecorationImageProps: Decodable, Equatable, Sendable {
    let source: String?
    let fit: String?
    let alignment: String?
    let opacity: ExprOr<Double>?
}

struct ShadowStyle: Decodable, Equatable, Sendable {
    let color: ExprOr<String>?
    let blur: ExprOr<Double>?
    let spreadRadius: ExprOr<Double>?
    let offset: ShadowOffset?
    let blurStyle: String?
}

struct ShadowOffset: Decodable, Equatable, Sendable {
    let x: ExprOr<Double>?
    let y: ExprOr<Double>?
}

struct GradientStyle: Decodable, Equatable, Sendable {
    let colors: [String]?
    let begin: String?
    let end: String?
    let colorList: [GradientColorStop]?

    var resolvedColors: [String]? {
        if let colors, !colors.isEmpty { return colors }
        let fromStops = colorList?.compactMap(\.color)
        return (fromStops?.isEmpty ?? true) ? nil : fromStops
    }
}

struct GradientColorStop: Decodable, Equatable, Sendable {
    let color: String?
    let stop: Double?
}

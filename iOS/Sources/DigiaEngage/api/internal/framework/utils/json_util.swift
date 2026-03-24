import Foundation

enum JsonUtil {
    static func decode<T: Decodable>(_ type: T.Type, from data: Data) throws -> T {
        try JSONDecoder().decode(T.self, from: data)
    }

    static func object(from data: Data) throws -> JsonLike {
        guard let object = try JSONSerialization.jsonObject(with: data) as? JsonLike else {
            throw NSError(domain: "Digia.JsonUtil", code: 1)
        }
        return object
    }
}

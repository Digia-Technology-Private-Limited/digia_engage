import SwiftUI

extension EdgeInsets {
    var isZero: Bool {
        top == 0 && leading == 0 && bottom == 0 && trailing == 0
    }
}

extension String {
    var nonEmpty: String? {
        isEmpty ? nil : self
    }
}

extension Bundle {
    func resourceURL(forDigiaAsset source: String) -> URL? {
        let normalized = source.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalized.isEmpty else { return nil }
        let nsPath = normalized as NSString
        let fileExtension = nsPath.pathExtension.isEmpty ? nil : nsPath.pathExtension
        let resourceName = fileExtension == nil ? normalized : nsPath.deletingPathExtension
        let subdirectory = nsPath.deletingLastPathComponent
        return url(
            forResource: resourceName,
            withExtension: fileExtension,
            subdirectory: subdirectory == "." ? nil : subdirectory
        )
    }
}

func decodeDashPattern<K: CodingKey>(from container: KeyedDecodingContainer<K>, forKey key: K) -> [Double]? {
    if let values = try? container.decodeIfPresent([Double].self, forKey: key) {
        return values
    }
    if let stringValue = try? container.decodeIfPresent(String.self, forKey: key) {
        return stringValue
            .split(separator: ",")
            .compactMap { Double($0.trimmingCharacters(in: .whitespacesAndNewlines)) }
    }
    return nil
}

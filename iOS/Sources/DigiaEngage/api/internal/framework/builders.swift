import SwiftUI

@MainActor
func createChildGroups(
    _ childGroups: [String: [JSONValue]],
    _ parent: VirtualWidget?,
    _ registry: VirtualWidgetRegistry
) throws -> [String: [VirtualWidget]]? {
    guard !childGroups.isEmpty else { return nil }
    let decoder = JSONDecoder()
    return try childGroups.mapValues { group in
        try group.map { jsonValue in
            // Encode then decode one level at a time — avoids recursive JSONDecoder stack frames.
            let data = try JSONEncoder().encode(jsonValue)
            let vwData = try decoder.decode(VWData.self, from: data)
            return try registry.createWidget(vwData, parent: parent)
        }
    }
}

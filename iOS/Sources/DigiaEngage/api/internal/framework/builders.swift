import SwiftUI

@MainActor
func createChildGroups(
    _ childGroups: [String: [VWData]],
    _ parent: VirtualWidget?,
    _ registry: VirtualWidgetRegistry
) throws -> [String: [VirtualWidget]]? {
    guard !childGroups.isEmpty else { return nil }
    return try childGroups.mapValues { group in
        try group.map { try registry.createWidget($0, parent: parent) }
    }
}

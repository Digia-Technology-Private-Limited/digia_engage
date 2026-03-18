import SwiftUI

enum ColorUtil {
    static func fromHex(_ value: String?) -> Color? {
        guard let value else { return nil }
        return Color(hex: value)
    }
}

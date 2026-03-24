import CoreText
import SwiftUI

public protocol DUIFontFactory {
    func getDefaultFont(
        size: Double,
        weight: Font.Weight,
        italic: Bool
    ) -> Font

    func getFont(
        _ fontFamily: String,
        size: Double,
        weight: Font.Weight,
        italic: Bool
    ) -> Font
}

public struct DefaultFontFactory: DUIFontFactory {
    public init() {}

    public func getDefaultFont(size: Double, weight: Font.Weight, italic: Bool) -> Font {
        var font = Font.system(size: size, weight: weight)
        if italic {
            font = font.italic()
        }
        return font
    }

    public func getFont(_ fontFamily: String, size: Double, weight: Font.Weight, italic: Bool) -> Font {
        getDefaultFont(size: size, weight: weight, italic: italic)
    }
}

public struct GoogleFontFactory: DUIFontFactory {
    public init() {
        DigiaBundledFontRegistrar.registerIfNeeded()
    }

    public func getDefaultFont(size: Double, weight: Font.Weight, italic: Bool) -> Font {
        getFont("Poppins", size: size, weight: weight, italic: italic)
    }

    public func getFont(_ fontFamily: String, size: Double, weight: Font.Weight, italic: Bool) -> Font {
        let normalizedFamily = fontFamily.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let familyToUse: String

        switch normalizedFamily {
        case "inter":
            familyToUse = "Inter"
        case "space grotesk", "spacegrotesk":
            familyToUse = "Poppins"
        case "poppins":
            familyToUse = "Poppins"
        default:
            familyToUse = "Poppins"
        }

        let customName = DigiaBundledFontRegistrar.customName(family: familyToUse, weight: weight)
        var font = Font.custom(customName, size: size)
        if italic {
            font = font.italic()
        }
        return font
    }
}

enum DigiaBundledFontRegistrar {
    private static let bundledFonts = [
        "Inter-Regular.ttf",
        "Inter-Medium.ttf",
        "Inter-SemiBold.ttf",
        "Inter-Bold.ttf",
        "Poppins-Regular.ttf",
        "Poppins-Medium.ttf",
        "Poppins-SemiBold.ttf",
        "Poppins-Bold.ttf",
    ]

    static func registerIfNeeded() {
        for file in bundledFonts {
            guard let url = DigiaResourceBundle.module.url(forResource: file, withExtension: nil, subdirectory: "Fonts") else { continue }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }

    static func customName(family: String, weight: Font.Weight) -> String {
        switch family.lowercased() {
        case "inter":
            return interName(for: weight)
        case "poppins":
            return poppinsName(for: weight)
        default:
            return "Poppins-Regular"
        }
    }

    private static func poppinsName(for weight: Font.Weight) -> String {
        switch weight {
        case .ultraLight, .thin, .light:
            return "Poppins-Regular"
        case .regular:
            return "Poppins-Regular"
        case .medium:
            return "Poppins-Medium"
        case .semibold:
            return "Poppins-SemiBold"
        case .bold, .heavy, .black:
            return "Poppins-Bold"
        default:
            return "Poppins-Regular"
        }
    }

    private static func interName(for weight: Font.Weight) -> String {
        switch weight {
        case .ultraLight, .thin, .light:
            return "Inter-Regular"
        case .regular:
            return "Inter-Regular"
        case .medium:
            return "Inter-Medium"
        case .semibold:
            return "Inter-SemiBold"
        case .bold, .heavy, .black:
            return "Inter-Bold"
        default:
            return "Inter-Regular"
        }
    }
}

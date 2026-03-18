import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

@MainActor
enum TextStyleUtil {
    private static let fallbackDescriptor = FontDescriptorProps(
        fontFamily: nil,
        weight: "regular",
        size: 14,
        height: 1.5,
        isItalic: false,
        style: false
    )

    static func font(
        textStyle: TextStyleProps?,
        appConfigStore: AppConfigStore,
        fontFactory: DUIFontFactory
    ) -> Font {
        font(
            descriptor: resolvedFontDescriptor(textStyle: textStyle, appConfigStore: appConfigStore),
            fontFactory: fontFactory
        )
    }

    static func font(
        descriptor: FontDescriptorProps?,
        fontFactory: DUIFontFactory
    ) -> Font {
        let size = descriptor?.size ?? 17
        let weight = To.fontWeight(descriptor?.weight)
        let italic = descriptor?.isItalic == true || descriptor?.style == true

        if let family = descriptor?.fontFamily, !family.isEmpty {
            return fontFactory.getFont(family, size: size, weight: weight, italic: italic)
        }

        return fontFactory.getDefaultFont(size: size, weight: weight, italic: italic)
    }

#if canImport(UIKit)
    static func uiFont(
        textStyle: TextStyleProps?,
        appConfigStore: AppConfigStore
    ) -> UIFont {
        uiFont(descriptor: resolvedFontDescriptor(textStyle: textStyle, appConfigStore: appConfigStore))
    }

    static func uiFont(
        descriptor: FontDescriptorProps?
    ) -> UIFont {
        let size = descriptor?.size ?? 17
        let weight = uiFontWeight(descriptor?.weight)
        let italic = descriptor?.isItalic == true || descriptor?.style == true

        let baseFont: UIFont
        if let family = descriptor?.fontFamily, !family.isEmpty,
           let custom = uiCustomFont(family: family, size: size, weight: To.fontWeight(descriptor?.weight)) {
            baseFont = custom
        } else {
            baseFont = .systemFont(ofSize: size, weight: weight)
        }

        guard italic else { return baseFont }
        guard let italicDescriptor = baseFont.fontDescriptor.withSymbolicTraits(.traitItalic) else {
            return .italicSystemFont(ofSize: size)
        }
        return UIFont(descriptor: italicDescriptor, size: size)
    }

    private static func uiCustomFont(family: String, size: Double, weight: Font.Weight) -> UIFont? {
        let normalizedFamily = family.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
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
        return UIFont(name: customName, size: size)
    }

    private static func uiFontWeight(_ value: String?) -> UIFont.Weight {
        switch value {
        case "thin":
            return .thin
        case "extralight", "extraLight", "extra-light":
            return .ultraLight
        case "light":
            return .light
        case "medium":
            return .medium
        case "semibold", "semiBold", "semi-bold":
            return .semibold
        case "bold":
            return .bold
        case "extrabold", "extraBold", "extra-bold":
            return .heavy
        case "black":
            return .black
        default:
            return .regular
        }
    }
#endif

    static func lineHeight(
        textStyle: TextStyleProps?,
        appConfigStore: AppConfigStore
    ) -> CGFloat? {
        guard let descriptor = resolvedFontDescriptor(textStyle: textStyle, appConfigStore: appConfigStore),
              let size = descriptor.size,
              let height = descriptor.height else {
            return nil
        }

        return CGFloat(size * height)
    }

    static func fontSize(
        textStyle: TextStyleProps?,
        appConfigStore: AppConfigStore
    ) -> CGFloat? {
        guard let descriptor = resolvedFontDescriptor(textStyle: textStyle, appConfigStore: appConfigStore),
              let size = descriptor.size else {
            return nil
        }

        return CGFloat(size)
    }

    static func resolvedFontDescriptor(
        textStyle: TextStyleProps?,
        appConfigStore: AppConfigStore
    ) -> FontDescriptorProps? {
        if let token = textStyle?.fontToken?.value,
           let tokenDescriptor = appConfigStore.themeFont(named: token) {
            return tokenDescriptor
        }

        if let inlineDescriptor = textStyle?.fontToken?.font,
           hasAnyInlineFontValue(inlineDescriptor) {
            return inlineDescriptor
        }

        return fallbackDescriptor
    }

    private static func hasAnyInlineFontValue(_ descriptor: FontDescriptorProps) -> Bool {
        descriptor.fontFamily != nil ||
            descriptor.weight != nil ||
            descriptor.size != nil ||
            descriptor.height != nil ||
            descriptor.isItalic != nil ||
            descriptor.style != nil
    }
}

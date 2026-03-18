import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

@MainActor
final class VWText: VirtualLeafStatelessWidget<TextProps> {
    override func render(_ payload: RenderPayload) -> AnyView {
        guard let text = payload.eval(props.text) else { return empty() }

        let fontFactory = DigiaRuntime.shared.fontFactory
        let font = TextStyleUtil.font(
            textStyle: props.textStyle,
            appConfigStore: payload.appConfigStore,
            fontFactory: fontFactory
        )
        let resolvedLineHeight = TextStyleUtil.lineHeight(
            textStyle: props.textStyle,
            appConfigStore: payload.appConfigStore
        )
        let resolvedFontSize = TextStyleUtil.fontSize(
            textStyle: props.textStyle,
            appConfigStore: payload.appConfigStore
        )
        let textColor = payload.resolveColor(props.textStyle?.textColor) ?? .primary
        let backgroundColor = payload.resolveColor(props.textStyle?.textBackgroundColor)
        let decoration = props.textStyle?.textDecoration?.lowercased()
        let decorationColor = payload.resolveColor(props.textStyle?.textDecorationColor) ?? textColor
        let lineLimit = payload.eval(props.maxLines)
        let alignment = payload.eval(props.alignment)
        let overflow = payload.eval(props.overflow)
        let hasGradient = !(props.textStyle?.gradient?.colorList?.isEmpty ?? true)
        let shouldExpandForParentStretch = (parent as? VWFlex)?.direction == .vertical &&
            (parent as? VWFlex)?.props.crossAxisAlignment == "stretch"
        let shouldExpandToAvailableWidth = commonProps?.style?.widthRaw == "100%" ||
            commonProps?.style?.width != nil ||
            lineLimit == 1 ||
            shouldExpandForParentStretch ||
            alignment == "center" ||
            alignment == "end" ||
            alignment == "right" ||
            alignment == "justify"

#if canImport(UIKit)
        if !hasGradient, overflow != "marquee" {
            return renderUIKitText(
                text: text,
                payload: payload,
                resolvedLineHeight: resolvedLineHeight,
                resolvedFontSize: resolvedFontSize,
                textColor: textColor,
                backgroundColor: backgroundColor,
                decoration: decoration,
                decorationColor: decorationColor,
                lineLimit: lineLimit,
                alignment: alignment,
                overflow: overflow,
                expandToAvailableWidth: shouldExpandToAvailableWidth
            )
        }
#endif

        return renderSwiftUIText(
            text: text,
            font: font,
            resolvedLineHeight: resolvedLineHeight,
            resolvedFontSize: resolvedFontSize,
            textColor: textColor,
            backgroundColor: backgroundColor,
            decoration: decoration,
            decorationColor: decorationColor,
            lineLimit: lineLimit,
            alignment: alignment,
            overflow: overflow,
            payload: payload,
            expandToAvailableWidth: shouldExpandToAvailableWidth
        )
    }

    private func renderSwiftUIText(
        text: String,
        font: Font,
        resolvedLineHeight: CGFloat?,
        resolvedFontSize: CGFloat?,
        textColor: Color,
        backgroundColor: Color?,
        decoration: String?,
        decorationColor: Color,
        lineLimit: Int?,
        alignment: String?,
        overflow: String?,
        payload: RenderPayload,
        expandToAvailableWidth: Bool
    ) -> AnyView {
        let base = Text(text)
            .font(font)
            .lineLimit(lineLimit)
            .multilineTextAlignment(To.textAlignment(alignment))

        var current: AnyView
        if let gradient = makeGradient(payload: payload) {
            current = AnyView(
                base
                    .foregroundColor(.clear)
                    .overlay(gradient.mask(base))
            )
        } else {
            current = AnyView(base.foregroundStyle(textColor))
        }

        if let resolvedLineHeight {
            let lineSpacing = max(0, resolvedLineHeight - (resolvedFontSize ?? resolvedLineHeight))
            current = AnyView(current.lineSpacing(lineSpacing))

            if lineLimit == 1 {
                current = AnyView(current.frame(minHeight: resolvedLineHeight, alignment: .center))
            }
        }

        current = applySharedDecorations(
            to: current,
            backgroundColor: backgroundColor,
            decoration: decoration,
            decorationColor: decorationColor
        )

        switch overflow {
        case "ellipsis":
            current = AnyView(current.truncationMode(.tail))
        case "fade":
            current = AnyView(
                current.mask(
                    LinearGradient(
                        colors: [.black, .black, .clear],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                )
            )
        case "marquee" where lineLimit == 1:
            current = AnyView(
                InternalMarquee(duration: 11, gap: 100) {
                    current.fixedSize(horizontal: true, vertical: false)
                }
            )
        case "visible":
            current = AnyView(current)
        case "clip":
            current = AnyView(current.clipped())
        default:
            break
        }

        if expandToAvailableWidth {
            current = AnyView(current.frame(maxWidth: .infinity, alignment: To.alignment(alignment) ?? .leading))
        }

        return current
    }

#if canImport(UIKit)
    private func renderUIKitText(
        text: String,
        payload: RenderPayload,
        resolvedLineHeight: CGFloat?,
        resolvedFontSize: CGFloat?,
        textColor: Color,
        backgroundColor: Color?,
        decoration: String?,
        decorationColor: Color,
        lineLimit: Int?,
        alignment: String?,
        overflow: String?,
        expandToAvailableWidth: Bool
    ) -> AnyView {
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = uiTextAlignment(for: alignment)
        paragraph.lineBreakMode = uiLineBreakMode(for: overflow)
        if let resolvedLineHeight {
            paragraph.minimumLineHeight = resolvedLineHeight
            paragraph.maximumLineHeight = resolvedLineHeight
        }

        var attributes: [NSAttributedString.Key: Any] = [
            .font: TextStyleUtil.uiFont(textStyle: props.textStyle, appConfigStore: payload.appConfigStore),
            .foregroundColor: UIColor(textColor),
            .paragraphStyle: paragraph,
        ]

        if let backgroundColor {
            attributes[.backgroundColor] = UIColor(backgroundColor)
        }
        if decoration == "underline" {
            attributes[.underlineStyle] = NSUnderlineStyle.single.rawValue
            attributes[.underlineColor] = UIColor(decorationColor)
        }
        if decoration == "linethrough" || decoration == "strikethrough" {
            attributes[.strikethroughStyle] = NSUnderlineStyle.single.rawValue
            attributes[.strikethroughColor] = UIColor(decorationColor)
        }

        var current = AnyView(
            InternalTextLabel(
                attributedText: NSAttributedString(string: text, attributes: attributes),
                alignment: paragraph.alignment,
                numberOfLines: lineLimit ?? 0,
                lineBreakMode: paragraph.lineBreakMode,
                clipsToBounds: overflow != "visible",
                expandToAvailableWidth: expandToAvailableWidth
            )
        )

        current = applySharedDecorations(
            to: current,
            backgroundColor: nil,
            decoration: decoration == "overline" ? "overline" : nil,
            decorationColor: decorationColor
        )

        if overflow == "fade" {
            current = AnyView(
                current.mask(
                    LinearGradient(
                        colors: [.black, .black, .clear],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                )
            )
        }

        if let minHeight = resolvedLineHeight ?? resolvedFontSize {
            current = AnyView(current.frame(minHeight: minHeight, alignment: .center))
        }

        if expandToAvailableWidth {
            current = AnyView(current.frame(maxWidth: .infinity, alignment: .leading))
        }

        return current
    }

    private func uiTextAlignment(for value: String?) -> NSTextAlignment {
        switch value {
        case "right", "end", "centerRight", "centerEnd":
            return .right
        case "center", "topCenter", "bottomCenter":
            return .center
        case "justify":
            return .justified
        default:
            return .left
        }
    }

    private func uiLineBreakMode(for overflow: String?) -> NSLineBreakMode {
        switch overflow {
        case "ellipsis":
            return .byTruncatingTail
        default:
            return .byClipping
        }
    }
#endif

    private func applySharedDecorations(
        to view: AnyView,
        backgroundColor: Color?,
        decoration: String?,
        decorationColor: Color
    ) -> AnyView {
        var current = view

        if decoration == "underline" {
            current = AnyView(current.underline(true, color: decorationColor))
        }
        if decoration == "linethrough" || decoration == "strikethrough" {
            current = AnyView(current.strikethrough(true, color: decorationColor))
        }
        if let backgroundColor {
            current = AnyView(current.background(backgroundColor))
        }
        if decoration == "overline" {
            current = AnyView(
                current.overlay(alignment: .topLeading) {
                    Rectangle()
                        .fill(decorationColor)
                        .frame(height: 1)
                        .offset(y: -1)
                }
            )
        }

        return current
    }

    private func makeGradient(payload: RenderPayload) -> LinearGradient? {
        guard let stops = props.textStyle?.gradient?.colorList,
              !stops.isEmpty else {
            return nil
        }
        let colors = stops.compactMap { stop in
            stop.color.flatMap(payload.resolveColor)
        }
        guard !colors.isEmpty else { return nil }
        return LinearGradient(
            colors: colors,
            startPoint: point(from: props.textStyle?.gradient?.begin) ?? .top,
            endPoint: point(from: props.textStyle?.gradient?.end) ?? .bottom
        )
    }

    private func point(from value: String?) -> UnitPoint? {
        switch value {
        case "topCenter": return .top
        case "bottomCenter": return .bottom
        case "center": return .center
        case "centerLeft", "centerStart", "left": return .leading
        case "centerRight", "centerEnd", "right": return .trailing
        case "topLeft", "topStart": return .topLeading
        case "topRight", "topEnd": return .topTrailing
        case "bottomLeft", "bottomStart": return .bottomLeading
        case "bottomRight", "bottomEnd": return .bottomTrailing
        default: return nil
        }
    }
}

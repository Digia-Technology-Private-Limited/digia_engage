import SwiftUI

@MainActor
final class VWRichText: VirtualLeafStatelessWidget<RichTextProps> {
    override func render(_ payload: RenderPayload) -> AnyView {
        let spans = props.textSpans.compactMap { span -> DigiaRichTextSpanViewModel? in
            guard let value = payload.eval(span.text), !value.isEmpty else { return nil }
            let style = span.resolvedStyle ?? props.textStyle
            return DigiaRichTextSpanViewModel(
                text: value,
                style: style,
                action: span.onClick,
                payload: payload
            )
        }

        guard !spans.isEmpty else { return empty() }

        var current = AnyView(
            DigiaWrappingFlowLayout(spacing: 0, lineSpacing: 0) {
                ForEach(Array(spans.enumerated()), id: \.offset) { _, span in
                    DigiaRichTextSpanView(span: span) {
                        payload.executeAction(span.action, triggerType: "onTap")
                    }
                }
            }
        )

        if payload.eval(props.overflow) == "fade" {
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

        if let alignment = To.alignment(payload.eval(props.alignment)) {
            current = AnyView(current.frame(maxWidth: .infinity, alignment: alignment))
        }

        return current
    }
}

@MainActor
private struct DigiaRichTextSpanViewModel: Identifiable {
    let id = UUID()
    let text: String
    let font: Font
    let foreground: AnyShapeStyle
    let backgroundColor: Color?
    let decoration: String?
    let decorationColor: Color
    let action: ActionFlow?

    init(
        text: String,
        style: TextStyleProps?,
        action: ActionFlow?,
        payload: RenderPayload
    ) {
        self.text = text
        font = TextStyleUtil.font(
            textStyle: style,
            appConfigStore: payload.appConfigStore,
            fontFactory: DigiaRuntime.shared.fontFactory
        )
        if let gradient = Self.gradient(for: style?.gradient, payload: payload) {
            foreground = AnyShapeStyle(gradient)
        } else {
            foreground = AnyShapeStyle(payload.resolveColor(style?.textColor) ?? .primary)
        }
        backgroundColor = payload.resolveColor(style?.textBackgroundColor)
        decoration = style?.textDecoration?.lowercased()
        decorationColor = payload.resolveColor(style?.textDecorationColor) ?? payload.resolveColor(style?.textColor) ?? .primary
        self.action = action
    }

    private static func gradient(for gradient: TextGradientProps?, payload: RenderPayload) -> LinearGradient? {
        guard let gradient,
              let stops = gradient.colorList,
              !stops.isEmpty else {
            return nil
        }

        let colors = stops.compactMap { stop in
            stop.color.flatMap(payload.resolveColor)
        }

        guard !colors.isEmpty else { return nil }

        return LinearGradient(
            colors: colors,
            startPoint: To.unitPoint(gradient.begin) ?? .top,
            endPoint: To.unitPoint(gradient.end) ?? .bottom
        )
    }
}

@MainActor
private struct DigiaRichTextSpanView: View {
    let span: DigiaRichTextSpanViewModel
    let onTap: () -> Void

    var body: some View {
        var current = AnyView(
            Text(span.text)
                .font(span.font)
                .foregroundStyle(span.foreground)
                .fixedSize()
        )

        if span.decoration == "underline" {
            current = AnyView(current.underline(true, color: span.decorationColor))
        }
        if span.decoration == "linethrough" || span.decoration == "strikethrough" {
            current = AnyView(current.strikethrough(true, color: span.decorationColor))
        }

        if let backgroundColor = span.backgroundColor {
            current = AnyView(current.background(backgroundColor))
        }

        if span.decoration == "overline" {
            current = AnyView(
                current.overlay(alignment: .topLeading) {
                    Rectangle()
                        .fill(span.decorationColor)
                        .frame(height: 1)
                        .offset(y: -1)
                }
            )
        }

        if let action = span.action, !action.isEmpty {
            current = AnyView(current.contentShape(Rectangle()).onTapGesture(perform: onTap))
        }

        return current
    }
}

private struct DigiaWrappingFlowLayout<Content: View>: View {
    let spacing: CGFloat
    let lineSpacing: CGFloat
    @ViewBuilder let content: () -> Content

    var body: some View {
        _DigiaWrappingLayout(spacing: spacing, lineSpacing: lineSpacing) {
            content()
        }
    }
}

private struct _DigiaWrappingLayout: Layout {
    let spacing: CGFloat
    let lineSpacing: CGFloat

    init(spacing: CGFloat, lineSpacing: CGFloat) {
        self.spacing = spacing
        self.lineSpacing = lineSpacing
    }

    func sizeThatFits(
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var lineWidth: CGFloat = 0
        var lineHeight: CGFloat = 0
        var totalHeight: CGFloat = 0
        var totalWidth: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if lineWidth + size.width > maxWidth, lineWidth > 0 {
                totalHeight += lineHeight + lineSpacing
                totalWidth = max(totalWidth, lineWidth - spacing)
                lineWidth = 0
                lineHeight = 0
            }

            lineWidth += size.width + spacing
            lineHeight = max(lineHeight, size.height)
        }

        totalHeight += lineHeight
        totalWidth = max(totalWidth, max(0, lineWidth - spacing))
        return CGSize(width: min(totalWidth, maxWidth), height: totalHeight)
    }

    func placeSubviews(
        in bounds: CGRect,
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) {
        var x = bounds.minX
        var y = bounds.minY
        var lineHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > bounds.maxX, x > bounds.minX {
                x = bounds.minX
                y += lineHeight + lineSpacing
                lineHeight = 0
            }

            subview.place(
                at: CGPoint(x: x, y: y),
                proposal: ProposedViewSize(width: size.width, height: size.height)
            )
            x += size.width + spacing
            lineHeight = max(lineHeight, size.height)
        }
    }
}

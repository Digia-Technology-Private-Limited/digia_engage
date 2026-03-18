import SDWebImageSwiftUI
import SwiftUI

@MainActor
final class VWContainer: VirtualStatelessWidget<ContainerProps> {
    override func toWidget(_ payload: RenderPayload) -> AnyView {
        if payload.eval(commonProps?.visibility) == false {
            return empty()
        }

        var current = render(payload)
        current = WidgetUtil.wrapInAlign(value: commonProps?.align, child: current)
        current = WidgetUtil.wrapInTapGesture(payload: payload, actionFlow: commonProps?.onClick, child: current)

        if let margin = props.margin?.edgeInsets {
            current = AnyView(current.padding(margin))
        }

        return current
    }

    override func render(_ payload: RenderPayload) -> AnyView {
        let width = payload.eval(props.width).map { CGFloat($0) }
        let height = payload.eval(props.height).map { CGFloat($0) }
        let minWidth = payload.eval(props.minWidth).map { CGFloat($0) }
        let minHeight = payload.eval(props.minHeight).map { CGFloat($0) }
        let maxWidth = payload.eval(props.maxWidth).map { CGFloat($0) }
        let maxHeight = payload.eval(props.maxHeight).map { CGFloat($0) }
        let radius = payload.eval(props.borderRadius) ?? payload.eval(props.border?.borderRadius) ?? 0
        let shape = props.shape == "circle" ? AnyShape(Circle()) : AnyShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
        let shouldFillWidth = width == nil && shouldFillAvailableWidth
        let shouldFillHeight = height == nil && shouldFillAvailableHeight
        let fallbackMinWidth: CGFloat? = child == nil && width == nil && hasVisibleDecoration ? 1 : nil
        let fallbackMinHeight: CGFloat? = child == nil && height == nil && hasVisibleDecoration ? 1 : nil
        let childAlignment = To.alignment(props.childAlignment)

        var content = child?.toWidget(payload)
            ?? AnyView(
                Color.clear.frame(
                    minWidth: fallbackMinWidth,
                    minHeight: fallbackMinHeight
                )
            )
        if let padding = props.padding?.edgeInsets {
            content = AnyView(content.padding(padding))
        }

        let hasBoundedWidth = shouldFillWidth || width != nil || maxWidth != nil || minWidth != nil
        let hasBoundedHeight = shouldFillHeight || height != nil || maxHeight != nil || minHeight != nil

        // Flutter parity: when alignment is set, Container expands (to incoming constraints)
        // so the child can be positioned. In SwiftUI we can't read constraints directly,
        // but maxWidth: .infinity is safe in the common bounded-width case (VStack/screen),
        // while we remain conservative on height to avoid unbounded ScrollView growth.
        if let alignment = childAlignment {
            content = AnyView(
                content.frame(
                    maxWidth: .infinity,
                    maxHeight: hasBoundedHeight ? .infinity : nil,
                    alignment: alignment
                )
            )
        }

        let resolvedMinWidth = width ?? max(minWidth ?? 0, fallbackMinWidth ?? 0)
        let resolvedMinHeight = height ?? max(minHeight ?? 0, fallbackMinHeight ?? 0)

        // Layout semantics:
        // - If width is specified, honor it.
        // - Otherwise, if maxWidth is specified, honor that.
        // - Otherwise expand only when the parent supplies tight fill constraints.
        // Flutter parity: if childAlignment is set, default to filling available width.
        let resolvedMaxWidth = width ?? maxWidth ?? (childAlignment != nil ? .infinity : (shouldFillWidth ? .infinity : nil))

        // For height, don't force .infinity by default; let the
        // surrounding layout (Column/List/etc.) drive vertical sizing,
        // unless an explicit height/maxHeight is provided or the parent is a
        // stack/positioned layout that expands on the main axis.
        let resolvedMaxHeight = height ?? maxHeight ?? (shouldFillHeight ? .infinity : nil)

        var current = AnyView(
            content.frame(
                minWidth: resolvedMinWidth,
                idealWidth: width,
                maxWidth: resolvedMaxWidth,
                minHeight: resolvedMinHeight,
                idealHeight: height,
                maxHeight: resolvedMaxHeight,
                alignment: .topLeading
            )
        )

        current = AnyView(
            current.background {
                decoratedBackground(payload: payload, shape: shape)
            }
        )

        if let border = props.border,
           let borderWidth = border.borderWidth,
           borderWidth > 0 {
            current = AnyView(
                current.overlay {
                    shape.stroke(
                        payload.evalColor(border.borderColor) ?? .black,
                        style: StrokeStyle(
                            lineWidth: borderWidth,
                            lineCap: To.strokeCap(border.borderType?.strokeCap),
                            dash: (border.borderType?.borderPattern == "solid" ? [] : (border.borderType?.dashPattern ?? [3, 1]).map { CGFloat($0) })
                        )
                    )
                }
            )
        }

        return current
    }

    // These helpers are retained for future use if we need more
    // nuanced behavior, but the core layout no longer relies on
    // inspecting the concrete parent type.
    private var shouldFillAvailableWidth: Bool {
        if let flexParent = parent as? VWFlex,
           flexParent.direction == .horizontal,
           parentProps?.expansion?.type == DigiaFlexFitType.tight.rawValue {
            return true
        }

        if let flexParent = parent as? VWFlex,
           flexParent.direction == .vertical,
           flexParent.props.crossAxisAlignment == "stretch" {
            return true
        }

        if let stackParent = parent as? VWStack {
            if stackParent.props.fit == "expand", parentProps?.position == nil {
                return true
            }

            if let position = parentProps?.position,
               position.left != nil,
               position.right != nil,
               props.width == nil {
                return true
            }
        }

        return false
    }

    private var shouldFillAvailableHeight: Bool {
        if let flexParent = parent as? VWFlex,
           flexParent.direction == .vertical,
           parentProps?.expansion?.type == DigiaFlexFitType.tight.rawValue {
            return true
        }

        if let flexParent = parent as? VWFlex,
           flexParent.direction == .horizontal,
           flexParent.props.crossAxisAlignment == "stretch" {
            return true
        }

        if let stackParent = parent as? VWStack {
            if stackParent.props.fit == "expand", parentProps?.position == nil {
                return true
            }

            if let position = parentProps?.position,
               position.top != nil,
               position.bottom != nil,
               props.height == nil {
                return true
            }
        }

        return false
    }

    private var hasVisibleDecoration: Bool {
        props.color != nil ||
        !(props.gradiant?.resolvedColors?.isEmpty ?? true) ||
        (props.border?.borderWidth ?? 0) > 0 ||
        !(props.shadow?.isEmpty ?? true)
    }

    @ViewBuilder
    private func containerBackground(payload: RenderPayload, shape: AnyShape) -> some View {
        ZStack {
            if let gradient = gradientView(payload: payload) {
                shape.fill(gradient)
            } else if let color = payload.evalColor(props.color) {
                shape.fill(color)
            }

            if let decorationImage = props.decorationImage,
               let source = decorationImage.source, !source.isEmpty {
                decorationImageView(decorationImage, source: source)
                    .opacity(payload.eval(decorationImage.opacity) ?? 1)
                    .clipShape(shape)
            }
        }
    }

    private func decoratedBackground(payload: RenderPayload, shape: AnyShape) -> AnyView {
        var background = AnyView(containerBackground(payload: payload, shape: shape))

        if let elevation = props.elevation, elevation > 0 {
            background = AnyView(
                background.shadow(
                    color: Color.black.opacity(0.18),
                    radius: elevation,
                    x: 0,
                    y: elevation / 2
                )
            )
        }

        return AnyView(
            ZStack {
                if let shadows = props.shadow {
                    ForEach(Array(shadows.enumerated()), id: \.offset) { _, shadow in
                        self.shadowLayer(payload: payload, shape: shape, shadow: shadow)
                    }
                }
                background
            }
        )
    }

    private func gradientView(payload: RenderPayload) -> LinearGradient? {
        guard let colors = props.gradiant?.resolvedColors?.compactMap(payload.resolveColor),
              !colors.isEmpty else {
            return nil
        }

        return LinearGradient(
            colors: colors,
            startPoint: To.unitPoint(props.gradiant?.begin) ?? .topLeading,
            endPoint: To.unitPoint(props.gradiant?.end) ?? .bottomTrailing
        )
    }

    @ViewBuilder
    private func decorationImageView(_ props: DecorationImageProps, source: String) -> some View {
        if source.hasPrefix("http"), let url = URL(string: source) {
            WebImage(url: url)
                .resizable()
                .aspectRatio(contentMode: To.imageContentMode(props.fit))
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: To.alignment(props.alignment) ?? .center)
        } else {
            Image(source)
                .resizable()
                .aspectRatio(contentMode: To.imageContentMode(props.fit))
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: To.alignment(props.alignment) ?? .center)
        }
    }

    @ViewBuilder
    private func shadowLayer(payload: RenderPayload, shape: AnyShape, shadow: ShadowStyle) -> some View {
        let shadowColor = payload.evalColor(shadow.color) ?? Color.black.opacity(0.2)
        let blur = CGFloat(max(0, payload.eval(shadow.blur) ?? 0))
        let spread = CGFloat(payload.eval(shadow.spreadRadius) ?? 0)
        let x = CGFloat(payload.eval(shadow.offset?.x) ?? 0)
        let y = CGFloat(payload.eval(shadow.offset?.y) ?? 0)

        shape
            .fill(shadowColor)
            .padding(-spread)
            .blur(radius: blur)
            .offset(x: x, y: y)
            .allowsHitTesting(false)
    }
}

private struct AnyShape: Shape {
    private let pathBuilder: @Sendable (CGRect) -> Path

    init<S: Shape>(_ shape: S) {
        pathBuilder = { rect in
            shape.path(in: rect)
        }
    }

    func path(in rect: CGRect) -> Path {
        pathBuilder(rect)
    }
}

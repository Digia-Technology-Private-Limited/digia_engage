import SwiftUI

@MainActor
final class VWStack: VirtualStatelessWidget<StackProps> {
    override func render(_ payload: RenderPayload) -> AnyView {
        let children = self.children ?? []
        guard !children.isEmpty else { return empty() }

        let alignment = stackAlignment
        var stack = AnyView(
            ZStack(alignment: alignment) {
                ForEach(Array(children.enumerated()), id: \.offset) { _, child in
                    self.positioned(child: child, payload: payload)
                }
            }
        )

        if props.fit == "expand" {
            stack = AnyView(stack.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: alignment))
        }

        return stack
    }

    private func positioned(child: VirtualWidget, payload: RenderPayload) -> some View {
        guard let position = positionedProps(for: child) else {
            var view = child.toWidget(payload)

            if props.fit == "expand" {
                view = AnyView(
                    view.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: stackAlignment)
                )
            }

            return view
        }

        let top = payload.eval(position.top).map { CGFloat($0) }
        let bottom = payload.eval(position.bottom).map { CGFloat($0) }
        let left = payload.eval(position.left).map { CGFloat($0) }
        let right = payload.eval(position.right).map { CGFloat($0) }
        let width = payload.eval(position.width).map { CGFloat($0) }
        let height = payload.eval(position.height).map { CGFloat($0) }

        let alignment = positionedAlignment(
            top: top,
            bottom: bottom,
            left: left,
            right: right,
            stackAlignment: stackAlignment
        )

        var view = child.toWidget(payload)
        if left != nil, right != nil, width == nil {
            view = AnyView(view.frame(maxWidth: .infinity, alignment: .leading))
        }
        if top != nil, bottom != nil, height == nil {
            view = AnyView(view.frame(maxHeight: .infinity, alignment: .top))
        }

        return AnyView(
            view
                .frame(width: width, height: height, alignment: .topLeading)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: alignment)
                .padding(.leading, left ?? 0)
                .padding(.trailing, right ?? 0)
                .padding(.top, top ?? 0)
                .padding(.bottom, bottom ?? 0)
        )
    }

    private var stackAlignment: Alignment {
        To.alignment(props.childAlignment) ?? .topLeading
    }

    private func positionedProps(for child: VirtualWidget) -> PositionedProps? {
        (child as? VirtualLeafStatelessWidgetProtocol)?.parentPropsValue?.position
    }

    private func positionedAlignment(
        top: CGFloat?,
        bottom: CGFloat?,
        left: CGFloat?,
        right: CGFloat?,
        stackAlignment: Alignment
    ) -> Alignment {
        let vertical = top != nil ? VerticalAnchor.top : (bottom != nil ? .bottom : verticalAnchor(from: stackAlignment))
        let horizontal = left != nil ? HorizontalAnchor.leading : (right != nil ? .trailing : horizontalAnchor(from: stackAlignment))
        return alignment(horizontal: horizontal, vertical: vertical)
    }

    private func horizontalAnchor(from alignment: Alignment) -> HorizontalAnchor {
        switch alignment {
        case .topLeading, .leading, .bottomLeading:
            return .leading
        case .topTrailing, .trailing, .bottomTrailing:
            return .trailing
        default:
            return .center
        }
    }

    private func verticalAnchor(from alignment: Alignment) -> VerticalAnchor {
        switch alignment {
        case .topLeading, .top, .topTrailing:
            return .top
        case .bottomLeading, .bottom, .bottomTrailing:
            return .bottom
        default:
            return .center
        }
    }

    private func alignment(horizontal: HorizontalAnchor, vertical: VerticalAnchor) -> Alignment {
        switch (vertical, horizontal) {
        case (.top, .leading):
            return .topLeading
        case (.top, .center):
            return .top
        case (.top, .trailing):
            return .topTrailing
        case (.center, .leading):
            return .leading
        case (.center, .center):
            return .center
        case (.center, .trailing):
            return .trailing
        case (.bottom, .leading):
            return .bottomLeading
        case (.bottom, .center):
            return .bottom
        case (.bottom, .trailing):
            return .bottomTrailing
        }
    }
}

private enum HorizontalAnchor {
    case leading
    case center
    case trailing
}

private enum VerticalAnchor {
    case top
    case center
    case bottom
}

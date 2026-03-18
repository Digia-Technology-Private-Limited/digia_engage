import SwiftUI

@MainActor
class VirtualLeafStatelessWidget<PropsType>: VirtualWidget, VirtualLeafStatelessWidgetProtocol, ParentAssignableVirtualWidget {
    let props: PropsType
    let commonProps: CommonProps?
    let parentProps: ParentProps?
    let refName: String?
    weak var parent: VirtualWidget?

    var parentPropsValue: ParentProps? { parentProps }
    var commonAlignValue: String? { commonProps?.align }

    init(
        props: PropsType,
        commonProps: CommonProps?,
        parentProps: ParentProps?,
        parent: VirtualWidget?,
        refName: String?
    ) {
        self.props = props
        self.commonProps = commonProps
        self.parentProps = parentProps
        self.parent = parent
        self.refName = refName
    }

    func render(_ payload: RenderPayload) -> AnyView {
        empty()
    }

    func toWidget(_ payload: RenderPayload) -> AnyView {
        let extendedPayload = refName.map(payload.withExtendedHierarchy) ?? payload

        if extendedPayload.eval(commonProps?.visibility) == false {
            return empty()
        }

        var current = render(extendedPayload)
        current = WidgetUtil.wrapInContainer(payload: extendedPayload, style: commonProps?.style, child: current)
        current = WidgetUtil.wrapInAlign(value: commonProps?.align, child: current)
        current = WidgetUtil.wrapInTapGesture(payload: extendedPayload, actionFlow: commonProps?.onClick, child: current)
        current = WidgetUtil.applyMargin(style: commonProps?.style, child: current)
        return current
    }
}

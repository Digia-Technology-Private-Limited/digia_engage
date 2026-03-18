import SwiftUI

@MainActor
protocol VirtualWidgetRegistry {
    func createWidget(_ data: VWData, parent: VirtualWidget?) throws -> VirtualWidget
}

enum VirtualWidgetRegistryError: Error, Equatable {
    case unsupportedWidgetType(String)
}

@MainActor
final class DefaultVirtualWidgetRegistry: VirtualWidgetRegistry {
    func createWidget(_ data: VWData, parent: VirtualWidget?) throws -> VirtualWidget {
        switch data {
        case let .widget(node):
            switch node.props {
            case let .scaffold(props):
                let widget = VWScaffold(props: props, commonProps: node.commonProps, parentProps: node.parentProps, childGroups: nil, parent: parent, refName: node.refName)
                try attachChildGroupsIfNeeded(node.childGroups, to: widget)
                return widget
            case let .container(props):
                let widget = VWContainer(props: props, commonProps: node.commonProps, parentProps: node.parentProps, childGroups: nil, parent: parent, refName: node.refName)
                try attachChildGroupsIfNeeded(node.childGroups, to: widget)
                return widget
            case let .flex(props):
                let direction: VWFlex.Direction = node.type == "digia/row" ? .horizontal : .vertical
                let widget = VWFlex(direction: direction, props: props, commonProps: node.commonProps, parentProps: node.parentProps, childGroups: nil, parent: parent, refName: node.refName)
                try attachChildGroupsIfNeeded(node.childGroups, to: widget)
                return widget
            case let .stack(props):
                let widget = VWStack(props: props, commonProps: node.commonProps, parentProps: node.parentProps, childGroups: nil, parent: parent, refName: node.refName)
                try attachChildGroupsIfNeeded(node.childGroups, to: widget)
                return widget
            case let .text(props):
                return VWText(props: props, commonProps: node.commonProps, parentProps: node.parentProps, parent: parent, refName: node.refName)
            case let .richText(props):
                return VWRichText(props: props, commonProps: node.commonProps, parentProps: node.parentProps, parent: parent, refName: node.refName)
            case let .button(props):
                return VWButton(props: props, commonProps: node.commonProps, parentProps: node.parentProps, parent: parent, refName: node.refName)
            case let .gridView(props):
                let widget = VWGridView(props: props, commonProps: node.commonProps, parentProps: node.parentProps, childGroups: nil, parent: parent, refName: node.refName)
                try attachChildGroupsIfNeeded(node.childGroups, to: widget)
                return widget
            case let .streamBuilder(props):
                let widget = VWStreamBuilder(props: props, commonProps: node.commonProps, parentProps: node.parentProps, childGroups: nil, parent: parent, refName: node.refName)
                try attachChildGroupsIfNeeded(node.childGroups, to: widget)
                return widget
            case let .image(props):
                return VWImage(props: props, commonProps: node.commonProps, parentProps: node.parentProps, parent: parent, refName: node.refName)
            case let .lottie(props):
                return VWLottie(props: props, commonProps: node.commonProps, parentProps: node.parentProps, parent: parent, refName: node.refName)
            case let .sizedBox(props):
                return VWSizedBox(props: props, commonProps: node.commonProps, parentProps: node.parentProps, parent: parent, refName: node.refName)
            case .conditionalBuilder:
                // ConditionalBuilder needs its children; treat it as the parent for them.
                let widget = VWConditionalBuilder(parentProps: node.parentProps, childGroups: nil)
                try attachChildGroupsIfNeeded(node.childGroups, to: widget)
                return widget
            case let .conditionalItem(props):
                let widget = VWConditionItem(props: props, commonProps: node.commonProps, parentProps: node.parentProps, childGroups: nil, parent: parent, refName: node.refName)
                try attachChildGroupsIfNeeded(node.childGroups, to: widget)
                return widget
            case let .linearProgressBar(props):
                return VWLinearProgressBar(props: props, commonProps: node.commonProps, parentProps: node.parentProps, parent: parent, refName: node.refName)
            case let .circularProgressBar(props):
                return VWCircularProgressBar(props: props, commonProps: node.commonProps, parentProps: node.parentProps, parent: parent, refName: node.refName)
            case let .carousel(props):
                let widget = VWCarousel(props: props, commonProps: node.commonProps, parentProps: node.parentProps, childGroups: nil, parent: parent, refName: node.refName)
                try attachChildGroupsIfNeeded(node.childGroups, to: widget)
                return widget
            case let .wrap(props):
                let widget = VWWrap(props: props, commonProps: node.commonProps, parentProps: node.parentProps, childGroups: nil, parent: parent, refName: node.refName)
                try attachChildGroupsIfNeeded(node.childGroups, to: widget)
                return widget
            case let .story(props):
                let widget = VWStory(props: props, commonProps: node.commonProps, parentProps: node.parentProps, childGroups: nil, parent: parent, refName: node.refName)
                try attachChildGroupsIfNeeded(node.childGroups, to: widget)
                return widget
            case let .storyVideoPlayer(props):
                return VWStoryVideoPlayer(props: props, commonProps: node.commonProps, parentProps: node.parentProps, parent: parent, refName: node.refName)
            case let .textFormField(props):
                let widget = VWTextFormField(props: props, commonProps: node.commonProps, parentProps: node.parentProps, childGroups: nil, parent: parent, refName: node.refName)
                try attachChildGroupsIfNeeded(node.childGroups, to: widget)
                return widget
            case let .videoPlayer(props):
                return VWVideoPlayer(props: props, commonProps: node.commonProps, parentProps: node.parentProps, parent: parent, refName: node.refName)
            case .unsupported:
                let detail: String?
                if node.type == "digia/unsupported", case let .string(reason)? = node.repeatData {
                    detail = reason
                } else {
                    detail = nil
                }
                return VWUnsupported(type: node.type, detail: detail, commonProps: node.commonProps, parentProps: node.parentProps, parent: parent, refName: node.refName)
            }

        case let .state(state):
            return VWStateContainer(initStateDefs: state.initStateDefs, parentProps: state.parentProps, childGroups: try createChildGroups(state.childGroups, parent, self), parent: parent, refName: state.refName)

        case let .component(component):
            return VWComponentHost(componentId: component.id, args: component.args ?? [:], commonProps: component.commonProps, parentProps: component.parentProps, parent: parent, refName: component.refName, registry: self)
        }
    }

    private func attachChildGroupsIfNeeded(_ nodeGroups: [String: [VWData]], to widget: VirtualWidget) throws {
        guard !nodeGroups.isEmpty else { return }
        let groups = try createChildGroups(nodeGroups, widget, self)
        (widget as? ChildGroupsAssignable)?.childGroups = groups
    }
}

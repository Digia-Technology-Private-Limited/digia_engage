import Foundation

enum VWDataBuilder {
    static func build(from root: ScopeValue?) -> VWData? {
        guard let root else { return nil }
        do {
            return try buildOrThrow(from: root)
        } catch {
            return nil
        }
    }

    private struct Frame {
        enum Stage { case enter, exit }

        let scope: ScopeValue
        let stage: Stage

        // Cached for exit stage
        let nodeKind: NodeKind?
        let childGroupPlan: [(key: String, count: Int)]?
        let totalChildren: Int?

        init(scope: ScopeValue) {
            self.scope = scope
            stage = .enter
            nodeKind = nil
            childGroupPlan = nil
            totalChildren = nil
        }

        init(
            scope: ScopeValue,
            nodeKind: NodeKind,
            childGroupPlan: [(key: String, count: Int)],
            totalChildren: Int
        ) {
            self.scope = scope
            stage = .exit
            self.nodeKind = nodeKind
            self.childGroupPlan = childGroupPlan
            self.totalChildren = totalChildren
        }
    }

    private enum NodeKind {
        case widget
        case state
        case component
    }

    private static func buildOrThrow(from root: ScopeValue) throws -> VWData? {
        var stack: [Frame] = [Frame(scope: root)]
        var built: [VWData] = []

        while let frame = stack.popLast() {
            switch frame.stage {
            case .enter:
                guard case let .object(object) = frame.scope else {
                    continue
                }

                let kind = nodeKind(from: object)

                let childGroupsScope = object["childGroups"] ?? object["composites"] ?? object["children"]
                let childGroups = normalizeChildGroups(childGroupsScope)

                var plan: [(String, Int)] = []
                plan.reserveCapacity(childGroups.count)
                var total = 0
                for (k, arr) in childGroups {
                    plan.append((k, arr.count))
                    total += arr.count
                }

                // Post-order build: push exit frame, then children enter frames.
                stack.append(
                    Frame(
                        scope: frame.scope,
                        nodeKind: kind,
                        childGroupPlan: plan,
                        totalChildren: total
                    )
                )

                // Push children in reverse to preserve input order.
                for (_, nodes) in childGroups.reversedStable() {
                    for node in nodes.reversed() {
                        stack.append(Frame(scope: node))
                    }
                }

            case .exit:
                guard let nodeKind = frame.nodeKind,
                      let plan = frame.childGroupPlan,
                      let totalChildren = frame.totalChildren,
                      case let .object(object) = frame.scope
                else {
                    continue
                }

                var childrenByKey: [String: [VWData]] = [:]
                if totalChildren > 0 {
                    let slice = built.suffix(totalChildren)
                    built.removeLast(totalChildren)

                    // slice is in build order and already matches original JSON order
                    // (we push children onto the stack in reverse).
                    let flat = Array(slice)

                    var index = 0
                    for (key, count) in plan {
                        if count == 0 {
                            childrenByKey[key] = []
                            continue
                        }
                        let end = index + count
                        if end <= flat.count {
                            childrenByKey[key] = Array(flat[index ..< end])
                        } else {
                            childrenByKey[key] = []
                        }
                        index = end
                    }
                }

                let node: VWData?
                switch nodeKind {
                case .widget:
                    node = try buildWidgetNode(object: object, childGroups: childrenByKey)
                case .state:
                    node = try buildStateNode(object: object, childGroups: childrenByKey)
                case .component:
                    node = try buildComponentNode(object: object)
                }

                if let node { built.append(node) }
            }
        }

        return built.last
    }

    private static func nodeKind(from object: [String: ScopeValue]) -> NodeKind {
        let raw = object["category"]?.stringValue ?? object["nodeType"]?.stringValue ?? "widget"
        switch raw {
        case "state": return .state
        case "component": return .component
        default: return .widget
        }
    }

    private static func normalizeChildGroups(_ scope: ScopeValue?) -> [(String, [ScopeValue])] {
        guard let scope else { return [] }

        switch scope {
        case let .object(object):
            // expected: { "children": [ ... ], "header": [ ... ] }
            return object.map { (key: $0.key, value: $0.value.arrayValue ?? []) }
        case let .array(array):
            // shorthand: [ ... ] -> treat as children
            return [("children", array)]
        default:
            return []
        }
    }

    private static func buildWidgetNode(
        object: [String: ScopeValue],
        childGroups: [String: [VWData]]
    ) throws -> VWData? {
        let type = (object["type"]?.stringValue ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let category = object["category"]?.stringValue ?? "widget"
        let refName = object["varName"]?.stringValue ?? object["refName"]?.stringValue

        let commonProps: CommonProps? = try decodeOptional(CommonProps.self, from: object["containerProps"])
        let parentProps: ParentProps? = try decodeOptional(ParentProps.self, from: object["parentProps"])

        let repeatData = object["repeatData"] ?? object["dataRef"]

        let propsScope = object["props"]
        let props = try decodeWidgetProps(type: type, propsScope: propsScope)

        return .widget(
            VWNodeData(
                category: category,
                type: type,
                props: props,
                commonProps: commonProps,
                parentProps: parentProps,
                childGroups: childGroups,
                repeatData: repeatData,
                refName: refName
            )
        )
    }

    private static func buildStateNode(
        object: [String: ScopeValue],
        childGroups: [String: [VWData]]
    ) throws -> VWData? {
        let category = object["category"]?.stringValue ?? "state"
        let refName = object["varName"]?.stringValue ?? object["refName"]?.stringValue
        let parentProps: ParentProps? = try decodeOptional(ParentProps.self, from: object["parentProps"])

        let initStateDefs = object["initStateDefs"]?.objectValue ?? [:]
        return .state(
            VWStateData(
                category: category,
                initStateDefs: initStateDefs,
                childGroups: childGroups,
                parentProps: parentProps,
                refName: refName
            )
        )
    }

    private static func buildComponentNode(object: [String: ScopeValue]) throws -> VWData? {
        let category = object["category"]?.stringValue ?? "component"
        let id = object["componentId"]?.stringValue ?? ""
        let args = object["componentArgs"]?.objectValue
        let refName = object["varName"]?.stringValue ?? object["refName"]?.stringValue
        let commonProps: CommonProps? = try decodeOptional(CommonProps.self, from: object["containerProps"])
        let parentProps: ParentProps? = try decodeOptional(ParentProps.self, from: object["parentProps"])

        return .component(
            VWComponentData(
                category: category,
                id: id,
                args: args,
                commonProps: commonProps,
                parentProps: parentProps,
                refName: refName
            )
        )
    }

    private static func decodeWidgetProps(type: String, propsScope: ScopeValue?) throws -> WidgetNodeProps {
        switch type {
        case "fw/scaffold", "digia/scaffold":
            return .scaffold(try decodeOrDefault(ScaffoldProps.self, from: propsScope, defaultValue: ScaffoldProps(scaffoldBackgroundColor: nil, enableSafeArea: nil, resizeToAvoidBottomInset: nil, body: nil, appBar: nil, drawer: nil, endDrawer: nil, bottomNavigationBar: nil, persistentFooterButtons: nil)))
        case "digia/container":
            return .container(try decodeOrDefault(ContainerProps.self, from: propsScope, defaultValue: ContainerProps(color: nil, padding: nil, margin: nil, width: nil, height: nil, minWidth: nil, minHeight: nil, maxWidth: nil, maxHeight: nil, childAlignment: nil, borderRadius: nil, border: nil, shape: nil, elevation: nil, decorationImage: nil, shadow: nil, gradiant: nil)))
        case "digia/column", "digia/row":
            return .flex(try decodeOrDefault(FlexProps.self, from: propsScope, defaultValue: FlexProps(spacing: nil, startSpacing: nil, endSpacing: nil, mainAxisAlignment: nil, crossAxisAlignment: nil, mainAxisSize: nil, isScrollable: nil, dataSource: nil)))
        case "digia/stack":
            return .stack(try decodeOrDefault(StackProps.self, from: propsScope, defaultValue: StackProps(childAlignment: nil, fit: nil)))
        case "digia/text":
            return .text(TextProps(scopeValue: propsScope))
        case "digia/richText":
            return .richText(try decode(RichTextProps.self, from: propsScope))
        case "digia/button":
            return .button(try decode(ButtonProps.self, from: propsScope))
        case "digia/gridView":
            return .gridView(try decode(GridViewProps.self, from: propsScope))
        case "digia/streamBuilder":
            return .streamBuilder(try decode(StreamBuilderProps.self, from: propsScope))
        case "digia/image":
            return .image(try decode(ImageProps.self, from: propsScope))
        case "digia/lottie":
            return .lottie(try decode(LottieProps.self, from: propsScope))
        case "fw/sized_box":
            return .sizedBox(try decodeOrDefault(SizedBoxProps.self, from: propsScope, defaultValue: SizedBoxProps(width: nil, height: nil)))
        case "digia/conditionalBuilder":
            return .conditionalBuilder(try decodeOrDefault(ConditionalBuilderProps.self, from: propsScope, defaultValue: ConditionalBuilderProps()))
        case "digia/conditionalItem":
            return .conditionalItem(try decode(ConditionalItemProps.self, from: propsScope))
        case "digia/linearProgressBar":
            return .linearProgressBar(try decode(LinearProgressBarProps.self, from: propsScope))
        case "digia/circularProgressBar":
            return .circularProgressBar(try decode(CircularProgressBarProps.self, from: propsScope))
        case "digia/carousel":
            return .carousel(try decode(CarouselProps.self, from: propsScope))
        case "digia/wrap":
            return .wrap(decodeLenient(WrapProps.self, from: propsScope))
        case "digia/story":
            return .story(decodeLenient(StoryProps.self, from: propsScope))
        case "digia/storyVideoPlayer":
            return .storyVideoPlayer(decodeLenient(StoryVideoPlayerProps.self, from: propsScope))
        case "digia/textFormField":
            return .textFormField(decodeLenient(TextFormFieldProps.self, from: propsScope))
        case "digia/videoPlayer":
            return .videoPlayer(decodeLenient(VideoPlayerProps.self, from: propsScope))
        default:
            return .unsupported
        }
    }

    // MARK: - Small decoding helpers (props-sized only)

    private static func decodeOptional<T: Decodable>(_ type: T.Type, from scope: ScopeValue?) throws -> T? {
        guard let scope, !scope.isNull else { return nil }
        return try decode(type, from: scope)
    }

    private static func decodeOrDefault<T: Decodable>(_ type: T.Type, from scope: ScopeValue?, defaultValue: T) throws -> T {
        guard let scope, !scope.isNull else { return defaultValue }
        return (try? decode(type, from: scope)) ?? defaultValue
    }

    private static func decodeLenient<T: Decodable>(_ type: T.Type, from scope: ScopeValue?) -> T {
        let normalized: ScopeValue = {
            guard let scope, !scope.isNull else { return .object([:]) }
            return scope
        }()

        if let decoded = try? decode(type, from: normalized) {
            return decoded
        }

        // Last resort: decode from empty object (all fields optional for these props).
        // If this throws, it's a programmer error (props schema not optional-friendly).
        // swiftlint:disable:next force_try
        return try! decode(type, from: .object([:]))
    }

    private static func decode<T: Decodable>(_ type: T.Type, from scope: ScopeValue?) throws -> T {
        guard let scope else {
            throw DecodingError.valueNotFound(
                T.self,
                DecodingError.Context(codingPath: [], debugDescription: "Missing ScopeValue payload")
            )
        }
        // Encode via ScopeValue's Codable conformance so primitives are supported too.
        // NSJSONSerialization rejects top-level primitives.
        let data = try JSONEncoder().encode(scope)
        return try JSONDecoder().decode(T.self, from: data)
    }

    private static func makeFallbackRoot(reason: String) -> VWData {
        .widget(
            VWNodeData(
                category: "widget",
                type: "digia/unsupported",
                props: .unsupported,
                commonProps: nil,
                parentProps: nil,
                childGroups: [:],
                repeatData: .string(reason),
                refName: "vwdata_builder_fallback"
            )
        )
    }
}

private extension ScopeValue {
    var isNull: Bool {
        if case .null = self { return true }
        return false
    }

    var stringValue: String? {
        if case let .string(v) = self { return v }
        return nil
    }

    var objectValue: [String: ScopeValue]? {
        if case let .object(v) = self { return v }
        return nil
    }

    var arrayValue: [ScopeValue]? {
        if case let .array(v) = self { return v }
        return nil
    }
}

private extension Array where Element == (String, [ScopeValue]) {
    func reversedStable() -> [(String, [ScopeValue])] { Array(reversed()) }
}


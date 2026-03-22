import Foundation

private struct DynamicCodingKey: CodingKey {
    var stringValue: String
    var intValue: Int? { nil }

    init?(stringValue: String) { self.stringValue = stringValue }
    init?(intValue: Int) { return nil }
}

enum VWData: Decodable, Equatable, Sendable {
    case widget(VWNodeData)
    case component(VWComponentData)

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let raw = try container.decodeIfPresent(String.self, forKey: .category)
        switch NodeType.fromString(raw) {
        case .component:
            self = .component(try VWComponentData(from: decoder))
        default:
            self = .widget(try VWNodeData(from: decoder))
        }
    }

    var refName: String? {
        switch self {
        case let .widget(data):
            return data.refName
        case let .component(data):
            return data.refName
        }
    }

    enum CodingKeys: String, CodingKey {
        case category
    }
}


struct VWNodeData: Decodable, Equatable, Sendable {
    let category: String
    let type: String
    let props: WidgetNodeProps
    let commonProps: CommonProps?
    let parentProps: ParentProps?
    /// Children stored as raw JSONValue to avoid recursive JSONDecoder stack frames.
    /// The registry decodes each child one level at a time when building the widget tree.
    let childGroups: [String: [JSONValue]]
    let repeatData: JSONValue?
    let refName: String?

    init(
        category: String,
        type: String,
        props: WidgetNodeProps,
        commonProps: CommonProps?,
        parentProps: ParentProps?,
        childGroups: [String: [JSONValue]],
        repeatData: JSONValue?,
        refName: String?
    ) {
        self.category = category
        self.type = type
        self.props = props
        self.commonProps = commonProps
        self.parentProps = parentProps
        self.childGroups = childGroups
        self.repeatData = repeatData
        self.refName = refName
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        type = try container.decode(String.self, forKey: .type)
        category = try container.decodeIfPresent(String.self, forKey: .category) ?? "widget"
        commonProps = try container.decodeIfPresent(CommonProps.self, forKey: .containerProps)
        parentProps = try container.decodeIfPresent(ParentProps.self, forKey: .parentProps)

        if let repeatValue = try container.decodeIfPresent(JSONValue.self, forKey: .repeatData) {
            repeatData = repeatValue
        } else if let dataRefValue = try container.decodeIfPresent(JSONValue.self, forKey: .dataRef) {
            repeatData = dataRefValue
        } else {
            repeatData = nil
        }

        refName = try container.decodeIfPresent(String.self, forKey: .varName) ?? container.decodeIfPresent(String.self, forKey: .refName)
        props = try Self.decodeProps(type: type, from: container)

        childGroups = try ChildGroups(from: decoder).value
    }

    enum CodingKeys: String, CodingKey {
        case category
        case type
        case props
        case containerProps
        case parentProps
        case repeatData
        case dataRef
        case varName
        case refName
    }

    private static func decodeProps(
        type: String,
        from container: KeyedDecodingContainer<CodingKeys>
    ) throws -> WidgetNodeProps {
        switch type {
        case "fw/scaffold", "digia/scaffold":
            return .scaffold(try container.decode(ScaffoldProps.self, forKey: .props))
        case "digia/container":
            return .container(try container.decode(ContainerProps.self, forKey: .props))
        case "digia/column", "digia/row":
            return .flex(try container.decode(FlexProps.self, forKey: .props))
        case "digia/stack":
            return .stack(try container.decode(StackProps.self, forKey: .props))
        case "digia/text":
            // Prefer JSONValue-based decode to avoid decoder recursion issues on large payloads.
            if let textScope = try container.decodeIfPresent(JSONValue.self, forKey: .props) {
                return .text(TextProps(JSONValue: textScope))
            }
            return .text(TextProps(JSONValue: nil))
        case "digia/richText":
            return .richText(try container.decode(RichTextProps.self, forKey: .props))
        case "digia/button":
            return .button(try container.decode(ButtonProps.self, forKey: .props))
        case "digia/gridView":
            return .gridView(try container.decode(GridViewProps.self, forKey: .props))
        case "digia/streamBuilder":
            return .streamBuilder(try container.decode(StreamBuilderProps.self, forKey: .props))
        case "digia/image":
            return .image(try container.decode(ImageProps.self, forKey: .props))
        case "digia/lottie":
            return .lottie(try container.decode(LottieProps.self, forKey: .props))
        case "fw/sized_box":
            return .sizedBox(try container.decodeIfPresent(SizedBoxProps.self, forKey: .props) ?? SizedBoxProps(width: nil, height: nil))
        case "digia/conditionalBuilder":
            return .conditionalBuilder(try container.decodeIfPresent(ConditionalBuilderProps.self, forKey: .props) ?? ConditionalBuilderProps())
        case "digia/conditionalItem":
            return .conditionalItem(try container.decode(ConditionalItemProps.self, forKey: .props))
        case "digia/linearProgressBar":
            return .linearProgressBar(try container.decode(LinearProgressBarProps.self, forKey: .props))
        case "digia/circularProgressBar":
            return .circularProgressBar(try container.decode(CircularProgressBarProps.self, forKey: .props))
        case "digia/carousel":
            return .carousel(try container.decode(CarouselProps.self, forKey: .props))
        case "digia/wrap":
            return .wrap(try container.decode(WrapProps.self, forKey: .props))
        case "digia/story":
            return .story(try container.decode(StoryProps.self, forKey: .props))
        case "digia/storyVideoPlayer":
            return .storyVideoPlayer(try container.decode(StoryVideoPlayerProps.self, forKey: .props))
        case "digia/textFormField":
            return .textFormField(try container.decode(TextFormFieldProps.self, forKey: .props))
        case "digia/videoPlayer":
            return .videoPlayer(try container.decode(VideoPlayerProps.self, forKey: .props))
        default:
            return .unsupported
        }
    }
}


struct VWComponentData: Decodable, Equatable, Sendable {
    let category: String
    let id: String
    let args: [String: JSONValue]?
    let commonProps: CommonProps?
    let parentProps: ParentProps?
    let refName: String?

    private enum CodingKeys: String, CodingKey {
        case category
        case componentId
        case componentArgs
        case containerProps
        case parentProps
        case varName
        case refName
    }

    init(
        category: String,
        id: String,
        args: [String: JSONValue]?,
        commonProps: CommonProps?,
        parentProps: ParentProps?,
        refName: String?
    ) {
        self.category = category
        self.id = id
        self.args = args
        self.commonProps = commonProps
        self.parentProps = parentProps
        self.refName = refName
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        category = try container.decodeIfPresent(String.self, forKey: .category) ?? "component"
        id = try container.decode(String.self, forKey: .componentId)
        args = try container.decodeIfPresent([String: JSONValue].self, forKey: .componentArgs)
        commonProps = try container.decodeIfPresent(CommonProps.self, forKey: .containerProps)
        parentProps = try container.decodeIfPresent(ParentProps.self, forKey: .parentProps)
        refName = try container.decodeIfPresent(String.self, forKey: .varName) ?? container.decodeIfPresent(String.self, forKey: .refName)
    }
}

enum WidgetNodeProps: Equatable, Sendable {
    case scaffold(ScaffoldProps)
    case container(ContainerProps)
    case flex(FlexProps)
    case stack(StackProps)
    case text(TextProps)
    case richText(RichTextProps)
    case button(ButtonProps)
    case gridView(GridViewProps)
    case streamBuilder(StreamBuilderProps)
    case image(ImageProps)
    case lottie(LottieProps)
    case sizedBox(SizedBoxProps)
    case conditionalBuilder(ConditionalBuilderProps)
    case conditionalItem(ConditionalItemProps)
    case linearProgressBar(LinearProgressBarProps)
    case circularProgressBar(CircularProgressBarProps)
    case carousel(CarouselProps)
    case wrap(WrapProps)
    case story(StoryProps)
    case storyVideoPlayer(StoryVideoPlayerProps)
    case textFormField(TextFormFieldProps)
    case videoPlayer(VideoPlayerProps)
    case unsupported

    static func decode(
        type: String,
        from container: KeyedDecodingContainer<VWNodeData.CodingKeys>,
        forKey key: VWNodeData.CodingKeys
    ) throws -> WidgetNodeProps {
        let decoded: WidgetNodeProps
        switch type {
        case "fw/scaffold", "digia/scaffold":
            decoded = .scaffold(try container.decodeIfPresent(ScaffoldProps.self, forKey: key) ?? ScaffoldProps(scaffoldBackgroundColor: nil, enableSafeArea: nil, resizeToAvoidBottomInset: nil, body: nil, appBar: nil, drawer: nil, endDrawer: nil, bottomNavigationBar: nil, persistentFooterButtons: nil))
        case "digia/container":
            decoded = .container(try container.decodeIfPresent(ContainerProps.self, forKey: key) ?? ContainerProps(color: nil, padding: nil, margin: nil, width: nil, height: nil, minWidth: nil, minHeight: nil, maxWidth: nil, maxHeight: nil, childAlignment: nil, borderRadius: nil, border: nil, shape: nil, elevation: nil, decorationImage: nil, shadow: nil, gradiant: nil))
        case "digia/column", "digia/row":
            decoded = .flex(try container.decodeIfPresent(FlexProps.self, forKey: key) ?? FlexProps(spacing: nil, startSpacing: nil, endSpacing: nil, mainAxisAlignment: nil, crossAxisAlignment: nil, mainAxisSize: nil, isScrollable: nil, dataSource: nil))
        case "digia/stack":
            decoded = .stack(try container.decodeIfPresent(StackProps.self, forKey: key) ?? StackProps(childAlignment: nil, fit: nil))
        case "digia/text":
            // Work around deep nested decoder crashes by decoding text props through JSONValue.
            if let textScope = try container.decodeIfPresent(JSONValue.self, forKey: key) {
                decoded = .text(TextProps(JSONValue: textScope))
            } else {
                decoded = .text(TextProps(JSONValue: nil))
            }
        case "digia/richText":
            decoded = .richText(try container.decode(RichTextProps.self, forKey: key))
        case "digia/button":
            decoded = .button(try container.decode(ButtonProps.self, forKey: key))
        case "digia/gridView":
            decoded = .gridView(try container.decode(GridViewProps.self, forKey: key))
        case "digia/streamBuilder":
            decoded = .streamBuilder(try container.decode(StreamBuilderProps.self, forKey: key))
        case "digia/image":
            decoded = .image(try container.decode(ImageProps.self, forKey: key))
        case "digia/lottie":
            decoded = .lottie(try container.decode(LottieProps.self, forKey: key))
        case "fw/sized_box":
            decoded = .sizedBox(try container.decodeIfPresent(SizedBoxProps.self, forKey: key) ?? SizedBoxProps(width: nil, height: nil))
        case "digia/conditionalBuilder":
            decoded = .conditionalBuilder(try container.decodeIfPresent(ConditionalBuilderProps.self, forKey: key) ?? ConditionalBuilderProps())
        case "digia/conditionalItem":
            decoded = .conditionalItem(try container.decode(ConditionalItemProps.self, forKey: key))
        case "digia/linearProgressBar":
            decoded = .linearProgressBar(try container.decode(LinearProgressBarProps.self, forKey: key))
        case "digia/circularProgressBar":
            decoded = .circularProgressBar(try container.decode(CircularProgressBarProps.self, forKey: key))
        case "digia/carousel":
            decoded = .carousel(try container.decode(CarouselProps.self, forKey: key))
        case "digia/wrap":
            decoded = .wrap(try container.decode(WrapProps.self, forKey: key))
        case "digia/story":
            decoded = .story(try container.decode(StoryProps.self, forKey: key))
        case "digia/storyVideoPlayer":
            decoded = .storyVideoPlayer(try container.decode(StoryVideoPlayerProps.self, forKey: key))
        case "digia/textFormField":
            decoded = .textFormField(try container.decode(TextFormFieldProps.self, forKey: key))
        case "digia/videoPlayer":
            decoded = .videoPlayer(try container.decode(VideoPlayerProps.self, forKey: key))
        default:
            decoded = .unsupported
        }
        return decoded
    }
}

private struct ChildGroups: Decodable, Equatable, Sendable {
    let value: [String: [JSONValue]]

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        if container.contains(.childGroups) {
            value = try Self.decodeGroupValue(from: container, key: .childGroups)
            return
        }
        if container.contains(.composites) {
            value = try Self.decodeGroupValue(from: container, key: .composites)
            return
        }
        if container.contains(.children) {
            value = try Self.decodeGroupValue(from: container, key: .children)
            return
        }

        value = [:]
    }

    private enum CodingKeys: String, CodingKey {
        case children
        case composites
        case childGroups
    }

    private static func decodeGroupValue(
        from container: KeyedDecodingContainer<CodingKeys>,
        key: CodingKeys
    ) throws -> [String: [JSONValue]] {
        let groupDecoder = try container.superDecoder(forKey: key)

        // Decode children as JSONValue (not VWData) to avoid recursive JSONDecoder stack
        // frames for deeply nested widget trees. The registry decodes VWData lazily.
        if let keyed = try? groupDecoder.container(keyedBy: DynamicCodingKey.self) {
            var result: [String: [JSONValue]] = [:]
            result.reserveCapacity(keyed.allKeys.count)
            for k in keyed.allKeys {
                var arrayContainer = try keyed.nestedUnkeyedContainer(forKey: k)
                var items: [JSONValue] = []
                items.reserveCapacity(arrayContainer.count ?? 0)
                while !arrayContainer.isAtEnd {
                    items.append(try arrayContainer.decode(JSONValue.self))
                }
                result[k.stringValue] = items
            }
            return result
        }

        if var unkeyed = try? groupDecoder.unkeyedContainer() {
            var items: [JSONValue] = []
            items.reserveCapacity(unkeyed.count ?? 0)
            while !unkeyed.isAtEnd {
                items.append(try unkeyed.decode(JSONValue.self))
            }
            return ["children": items]
        }

        return [:]
    }
}

import Foundation

struct TextProps: Codable, Equatable, Sendable {
    let text: ExprOr<String>?
    let textStyle: TextStyleProps?
    let maxLines: ExprOr<Int>?
    let alignment: ExprOr<String>?
    let overflow: ExprOr<String>?

    var fontDescriptor: FontDescriptorProps? { textStyle?.fontToken?.font }

    init(
        text: ExprOr<String>?,
        textStyle: TextStyleProps?,
        maxLines: ExprOr<Int>?,
        alignment: ExprOr<String>?,
        overflow: ExprOr<String>?
    ) {
        self.text = text
        self.textStyle = textStyle
        self.maxLines = maxLines
        self.alignment = alignment
        self.overflow = overflow
    }

    private enum CodingKeys: String, CodingKey {
        case text
        case textStyle
        case maxLines
        case alignment
        case overflow
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        // Decode through ScopeValue first to avoid deep nested decoder crashes on large payloads.
        if let textScope = try container.decodeIfPresent(ScopeValue.self, forKey: .text) {
            text = ExprOr<String>.fromScopeValue(textScope)
        } else {
            text = nil
        }

        if let textStyleScope = try container.decodeIfPresent(ScopeValue.self, forKey: .textStyle) {
            textStyle = TextStyleProps(scopeValue: textStyleScope)
        } else {
            textStyle = nil
        }

        if let maxLinesScope = try container.decodeIfPresent(ScopeValue.self, forKey: .maxLines) {
            maxLines = ExprOr<Int>.fromScopeValue(maxLinesScope)
        } else {
            maxLines = nil
        }

        if let alignmentScope = try container.decodeIfPresent(ScopeValue.self, forKey: .alignment) {
            alignment = ExprOr<String>.fromScopeValue(alignmentScope)
        } else {
            alignment = nil
        }

        if let overflowScope = try container.decodeIfPresent(ScopeValue.self, forKey: .overflow) {
            overflow = ExprOr<String>.fromScopeValue(overflowScope)
        } else {
            overflow = nil
        }
    }

    init(scopeValue: ScopeValue?) {
        let object = scopeValue?.duiObjectValue ?? [:]
        text = ExprOr<String>.fromScopeValue(object["text"])
        textStyle = TextStyleProps(scopeValue: object["textStyle"])
        maxLines = ExprOr<Int>.fromScopeValue(object["maxLines"])
        alignment = ExprOr<String>.fromScopeValue(object["alignment"])
        overflow = ExprOr<String>.fromScopeValue(object["overflow"])
    }
}

extension TextStyleProps {
    init?(scopeValue: ScopeValue?) {
        guard let object = scopeValue?.duiObjectValue else { return nil }
        self.init(
            fontToken: FontTokenProps(scopeValue: object["fontToken"]),
            textColor: object["textColor"]?.duiStringValue,
            textBackgroundColor: object["textBackgroundColor"]?.duiStringValue,
            textDecoration: object["textDecoration"]?.duiStringValue,
            textDecorationColor: object["textDecorationColor"]?.duiStringValue,
            gradient: TextGradientProps(scopeValue: object["gradient"])
        )
    }
}

extension FontTokenProps {
    init?(scopeValue: ScopeValue?) {
        guard let object = scopeValue?.duiObjectValue else { return nil }
        self.init(
            value: object["value"]?.duiStringValue,
            font: FontDescriptorProps(scopeValue: object["font"])
        )
    }
}

extension FontDescriptorProps {
    init?(scopeValue: ScopeValue?) {
        guard let object = scopeValue?.duiObjectValue else { return nil }

        let familyValue = Self.resolveFamily(from: object)

        self.init(
            fontFamily: familyValue,
            weight: object["weight"]?.duiStringValue,
            size: object["size"]?.duiDoubleLikeValue,
            height: object["height"]?.duiDoubleLikeValue,
            isItalic: object["isItalic"]?.duiBoolLikeValue,
            style: {
                if let boolValue = object["style"]?.duiBoolValue {
                    return boolValue
                }
                if let styleString = object["style"]?.duiStringValue {
                    let normalized = styleString.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                    if normalized == "italic" { return true }
                    if normalized == "normal" { return false }
                }
                return nil
            }()
        )
    }

    private static func resolveFamily(from object: [String: ScopeValue]) -> String? {
        if let direct = object["fontFamily"]?.duiStringValue {
            return direct
        }
        if let directKebab = object["font-family"]?.duiStringValue {
            return directKebab
        }

        let familyObject = object["fontFamily"]?.duiObjectValue
        if let primary = familyObject?["primary"]?.duiStringValue {
            return primary
        }
        if let secondary = familyObject?["secondary"]?.duiStringValue {
            return secondary
        }

        let familyKebabObject = object["font-family"]?.duiObjectValue
        if let primary = familyKebabObject?["primary"]?.duiStringValue {
            return primary
        }
        if let secondary = familyKebabObject?["secondary"]?.duiStringValue {
            return secondary
        }

        return nil
    }
}

extension TextGradientProps {
    init?(scopeValue: ScopeValue?) {
        guard let object = scopeValue?.duiObjectValue else { return nil }
        self.init(
            type: object["type"]?.duiStringValue,
            begin: object["begin"]?.duiStringValue,
            end: object["end"]?.duiStringValue,
            colorList: object["colorList"]?.duiArrayValue?.compactMap(TextGradientStop.init(scopeValue:))
        )
    }
}

extension TextGradientStop {
    init?(scopeValue: ScopeValue?) {
        guard let object = scopeValue?.duiObjectValue else { return nil }
        self.init(
            color: object["color"]?.duiStringValue,
            stop: object["stop"]?.duiDoubleLikeValue
        )
    }
}

extension ExprOr where Value == String {
    static func fromScopeValue(_ value: ScopeValue?) -> ExprOr<String>? {
        switch value {
        case let .string(raw):
            return .value(raw)
        case let .object(object):
            if let expr = object["expr"]?.duiStringValue {
                return .expression(expr)
            }
            return nil
        case let .int(number):
            return .value(String(number))
        case let .double(number):
            return .value(String(number))
        case let .bool(flag):
            return .value(String(flag))
        default:
            return nil
        }
    }
}

extension ExprOr where Value == Int {
    static func fromScopeValue(_ value: ScopeValue?) -> ExprOr<Int>? {
        switch value {
        case let .int(number):
            return .value(number)
        case let .double(number):
            return .value(Int(number))
        case let .string(raw):
            if let intValue = Int(raw) {
                return .value(intValue)
            }
            return .expression(raw)
        case let .object(object):
            if let expr = object["expr"]?.duiStringValue {
                return .expression(expr)
            }
            return nil
        default:
            return nil
        }
    }
}

extension ScopeValue {
    var duiStringValue: String? {
        if case let .string(value) = self { return value }
        return nil
    }

    var duiBoolValue: Bool? {
        if case let .bool(value) = self { return value }
        return nil
    }

    var duiBoolLikeValue: Bool? {
        switch self {
        case let .bool(value):
            return value
        case let .string(value):
            return Bool(value.lowercased())
        default:
            return nil
        }
    }

    var duiDoubleLikeValue: Double? {
        switch self {
        case let .double(value):
            return value
        case let .int(value):
            return Double(value)
        case let .string(value):
            return Double(value)
        default:
            return nil
        }
    }

    var duiObjectValue: [String: ScopeValue]? {
        if case let .object(value) = self { return value }
        return nil
    }

    var duiArrayValue: [ScopeValue]? {
        if case let .array(value) = self { return value }
        return nil
    }
}

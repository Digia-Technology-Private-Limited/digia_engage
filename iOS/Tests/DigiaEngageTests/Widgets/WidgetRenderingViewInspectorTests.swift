import Foundation
import DigiaExpr
@testable import DigiaEngage
import Testing
import ViewInspector
import SwiftUI

@MainActor
@Suite("Widget Rendering ViewInspector")
struct WidgetRenderingViewInspectorTests {
    @Test("text widget renders resolved text")
    func textWidgetRendersResolvedText() throws {
        let widget = VWText(
            props: TextProps(
                text: .value("Hello from widget"),
                textStyle: TextStyleProps(
                    fontToken: nil,
                    textColor: nil,
                    textBackgroundColor: nil,
                    textDecoration: nil,
                    textDecorationColor: nil,
                    gradient: TextGradientProps(
                        type: nil,
                        begin: nil,
                        end: nil,
                        colorList: [TextGradientStop(color: "#ffffff", stop: 0)]
                    )
                ),
                maxLines: nil,
                alignment: nil,
                overflow: nil
            ),
            commonProps: nil,
            parentProps: nil,
            parent: nil,
            refName: "text_1"
        )

        let rendered = widget.toWidget(RenderPayload(appConfigStore: AppConfigStore()))
        let text = try rendered.inspect().find(ViewType.Text.self).string()

        #expect(text == "Hello from widget")
    }

    @Test("button widget renders button label")
    func buttonWidgetRendersLabel() throws {
        let widget = VWButton(
            props: ButtonProps(
                buttonState: nil,
                isDisabled: .value(false),
                disabledStyle: nil,
                defaultStyle: nil,
                text: ButtonTextProps(
                    text: .value("Tap me"),
                    textStyle: nil,
                    maxLines: nil,
                    overflow: nil
                ),
                leadingIcon: nil,
                trailingIcon: nil,
                shape: nil,
                onClick: nil
            ),
            commonProps: nil,
            parentProps: nil,
            parent: nil,
            refName: "button_1"
        )

        let rendered = widget.toWidget(RenderPayload(appConfigStore: AppConfigStore()))
        let text = try rendered.inspect().find(ViewType.Text.self).string()

        #expect(text == "Tap me")
    }

    @Test("story widget renders current item and overlays")
    func storyWidgetRendersCurrentItem() throws {
        let item = VWText(
            props: TextProps(
                text: .value("Story page"),
                textStyle: TextStyleProps(
                    fontToken: nil,
                    textColor: nil,
                    textBackgroundColor: nil,
                    textDecoration: nil,
                    textDecorationColor: nil,
                    gradient: TextGradientProps(type: nil, begin: nil, end: nil, colorList: [TextGradientStop(color: "#ffffff", stop: 0)])
                ),
                maxLines: nil,
                alignment: nil,
                overflow: nil
            ),
            commonProps: nil,
            parentProps: nil,
            parent: nil,
            refName: nil
        )
        let header = VWText(
            props: TextProps(
                text: .value("Header"),
                textStyle: TextStyleProps(
                    fontToken: nil,
                    textColor: nil,
                    textBackgroundColor: nil,
                    textDecoration: nil,
                    textDecorationColor: nil,
                    gradient: TextGradientProps(type: nil, begin: nil, end: nil, colorList: [TextGradientStop(color: "#ffffff", stop: 0)])
                ),
                maxLines: nil,
                alignment: nil,
                overflow: nil
            ),
            commonProps: nil,
            parentProps: nil,
            parent: nil,
            refName: nil
        )

        let widget = VWStory(
            props: StoryProps(
                dataSource: nil,
                controller: nil,
                onSlideDown: nil,
                onSlideStart: nil,
                onLeftTap: nil,
                onRightTap: nil,
                onCompleted: nil,
                onPreviousCompleted: nil,
                onStoryChanged: nil,
                indicator: nil,
                initialIndex: .value(0),
                restartOnCompleted: .value(false),
                duration: .value(3000)
            ),
            commonProps: nil,
            parentProps: nil,
            childGroups: [
                "items": [item],
                "header": [header],
            ],
            parent: nil,
            refName: "story_1"
        )

        let rendered = widget.toWidget(RenderPayload(appConfigStore: AppConfigStore()))
        let texts = try rendered.inspect().findAll(ViewType.Text.self).map { try $0.string() }

        #expect(texts.contains("Story page"))
        #expect(texts.contains("Header"))
    }

    @Test("wrap widget renders repeated child content")
    func wrapWidgetRendersRepeatedChildContent() throws {
        let child = VWText(
            props: TextProps(
                text: .expression("${item.currentItem}"),
                textStyle: TextStyleProps(
                    fontToken: nil,
                    textColor: nil,
                    textBackgroundColor: nil,
                    textDecoration: nil,
                    textDecorationColor: nil,
                    gradient: TextGradientProps(type: nil, begin: nil, end: nil, colorList: [TextGradientStop(color: "#ffffff", stop: 0)])
                ),
                maxLines: nil,
                alignment: nil,
                overflow: nil
            ),
            commonProps: nil,
            parentProps: nil,
            parent: nil,
            refName: nil
        )
        let widget = VWWrap(
            props: WrapProps(
                dataSource: .string("${state.items}"),
                spacing: .value(8),
                wrapAlignment: nil,
                wrapCrossAlignment: nil,
                direction: nil,
                runSpacing: nil,
                runAlignment: nil,
                verticalDirection: nil,
                clipBehavior: nil
            ),
            commonProps: nil,
            parentProps: nil,
            childGroups: ["children": [child]],
            parent: nil,
            refName: "item"
        )

        let payload = RenderPayload(
            appConfigStore: AppConfigStore(),
            scopeContext: BasicExprContext(variables: ["state": ["items": ["One", "Two"]]])
        )

        let rendered = widget.toWidget(payload)
        let texts = try rendered.inspect().findAll(ViewType.Text.self).map { try $0.string() }

        #expect(texts.contains("One"))
        #expect(texts.contains("Two"))
    }

    @Test("text form field renders label hint prefix and suffix")
    func textFormFieldRendersDecorations() throws {
        let prefix = VWText(
            props: TextProps(
                text: .value("P"),
                textStyle: TextStyleProps(
                    fontToken: nil,
                    textColor: nil,
                    textBackgroundColor: nil,
                    textDecoration: nil,
                    textDecorationColor: nil,
                    gradient: TextGradientProps(type: nil, begin: nil, end: nil, colorList: [TextGradientStop(color: "#ffffff", stop: 0)])
                ),
                maxLines: nil,
                alignment: nil,
                overflow: nil
            ),
            commonProps: nil,
            parentProps: nil,
            parent: nil,
            refName: nil
        )
        let suffix = VWText(
            props: TextProps(
                text: .value("S"),
                textStyle: TextStyleProps(
                    fontToken: nil,
                    textColor: nil,
                    textBackgroundColor: nil,
                    textDecoration: nil,
                    textDecorationColor: nil,
                    gradient: TextGradientProps(type: nil, begin: nil, end: nil, colorList: [TextGradientStop(color: "#ffffff", stop: 0)])
                ),
                maxLines: nil,
                alignment: nil,
                overflow: nil
            ),
            commonProps: nil,
            parentProps: nil,
            parent: nil,
            refName: nil
        )

        let widget = VWTextFormField(
            props: TextFormFieldProps(
                controller: nil,
                initialValue: nil,
                autoFocus: nil,
                enabled: .value(true),
                keyboardType: nil,
                textInputAction: nil,
                textStyle: nil,
                textAlign: nil,
                readOnly: nil,
                obscureText: nil,
                maxLines: nil,
                minLines: nil,
                maxLength: nil,
                debounceValue: nil,
                textCapitalization: nil,
                inputFormatters: nil,
                fillColor: nil,
                labelText: .value("Email"),
                labelStyle: nil,
                hintText: .value("Enter email"),
                hintStyle: nil,
                contentPadding: nil,
                focusColor: nil,
                cursorColor: nil,
                prefixIconConstraints: nil,
                suffixIconConstraints: nil,
                validationRules: nil,
                errorStyle: nil,
                enabledBorder: nil,
                disabledBorder: nil,
                focusedBorder: nil,
                focusedErrorBorder: nil,
                errorBorder: nil,
                onChanged: nil,
                onSubmit: nil
            ),
            commonProps: nil,
            parentProps: nil,
            childGroups: [
                "prefix": [prefix],
                "suffix": [suffix],
            ],
            parent: nil,
            refName: "input_1"
        )

        let rendered = widget.toWidget(RenderPayload(appConfigStore: AppConfigStore()))
        ViewHosting.host(view: rendered)
        defer { ViewHosting.expel() }
        let texts = try rendered.inspect().findAll(ViewType.Text.self).map { try $0.string() }

        #expect(texts.contains("Email"))
        #expect(texts.contains("Enter email"))
        #expect(texts.contains("P"))
        #expect(texts.contains("S"))
    }
}

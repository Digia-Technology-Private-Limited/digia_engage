import Foundation
import AVFoundation
@testable import DigiaEngage
import Testing

@Suite("Widget Runtime Parity")
struct WidgetRuntimeParityTests {
    @Test("common style decodes intrinsic and percentage sizing metadata")
    func commonStyleDecodesRawSizingMetadata() throws {
        let props: CommonProps = try decode("""
        {
          "style": {
            "width": "100%",
            "height": "intrinsic",
            "border": {
              "borderWidth": 2,
              "borderColor": "#111111",
              "strokeAlign": "center",
              "borderType": {
                "borderPattern": "dashed",
                "dashPattern": [4, 2],
                "strokeCap": "round"
              }
            }
          }
        }
        """)

        #expect(props.style?.widthRaw == "100%")
        #expect(props.style?.heightRaw == "intrinsic")
        #expect(props.style?.border?.borderColor == .value("#111111"))
        #expect(props.style?.border?.borderType?.borderPattern == "dashed")
        #expect(props.style?.border?.borderType?.dashPattern == [4, 2])
    }

    @Test("parent props decode expansion type and evaluatable positioned values")
    func parentPropsDecodeExpansionAndPosition() throws {
        let props: ParentProps = try decode("""
        {
          "expansion": {
            "type": "tight",
            "flexValue": "${state.flex}"
          },
          "position": {
            "left": "${state.left}",
            "top": 16,
            "width": 120
          }
        }
        """)

        #expect(props.expansion?.type == "tight")
        #expect(props.expansion?.flexValue == .expression("${state.flex}"))
        #expect(props.position?.left == .expression("${state.left}"))
        #expect(props.position?.top == .value(16))
        #expect(props.position?.width == .value(120))
    }

    @Test("rich text props coerce string and object spans into span array")
    func richTextPropsCoerceSpanInputs() throws {
        let props: RichTextProps = try decode("""
        {
          "textSpans": [
            "Hello",
            {
              "text": " world",
              "onClick": {
                "steps": []
              }
            }
          ]
        }
        """)

        #expect(props.textSpans.count == 2)
        #expect(props.textSpans.first?.text == .value("Hello"))
        #expect(props.textSpans.last?.text == .value(" world"))
        #expect(props.textSpans.last?.onClick?.steps.isEmpty == true)
    }

    @Test("carousel props decode data source and nested indicator settings")
    func carouselPropsDecodeDataSourceAndIndicatorSettings() throws {
        let props: CarouselProps = try decode("""
        {
          "width": "320",
          "dataSource": "${state.items}",
          "indicator": {
            "indicatorAvailable": {
              "showIndicator": true,
              "dotHeight": 10,
              "dotWidth": 12,
              "spacing": 6,
              "dotColor": "#999999",
              "activeDotColor": "#111111",
              "indicatorEffectType": "worm"
            }
          }
        }
        """)

        #expect(props.width == .expression("320"))
        #expect(props.dataSource == .string("${state.items}"))
        #expect(props.showIndicator == true)
        #expect(props.dotHeight == 10)
        #expect(props.dotWidth == 12)
        #expect(props.spacing == 6)
        #expect(props.indicatorEffectType == "worm")
    }

    @Test("wrap props decode layout and data source settings")
    func wrapPropsDecodeLayoutAndDataSourceSettings() throws {
        let props: WrapProps = try decode("""
        {
          "dataSource": "${state.items}",
          "spacing": 10,
          "runSpacing": 14,
          "wrapAlignment": "spaceBetween",
          "wrapCrossAlignment": "center",
          "runAlignment": "end",
          "direction": "vertical",
          "verticalDirection": "up",
          "clipBehavior": "hardEdge"
        }
        """)

        #expect(props.dataSource == .string("${state.items}"))
        #expect(props.spacing == .value(10))
        #expect(props.runSpacing == .value(14))
        #expect(props.wrapAlignment == .value("spaceBetween"))
        #expect(props.wrapCrossAlignment == .value("center"))
        #expect(props.runAlignment == .value("end"))
        #expect(props.direction == .value("vertical"))
        #expect(props.verticalDirection == .value("up"))
        #expect(props.clipBehavior == .value("hardEdge"))
    }

    @Test("text form field props decode validation formatting and borders")
    func textFormFieldPropsDecodeValidationFormattingAndBorders() throws {
        let props: TextFormFieldProps = try decode("""
        {
          "initialValue": "hello",
          "maxLines": 4,
          "minLines": 2,
          "maxLength": 12,
          "debounceValue": 250,
          "labelText": "Name",
          "hintText": "Enter name",
          "validationRules": [
            { "type": "required", "errorMessage": "Required" },
            { "type": "minLength", "errorMessage": "Too short", "data": 3 },
            { "type": "pattern", "errorMessage": "Invalid", "data": "^[a-z]+$" }
          ],
          "inputFormatters": [
            { "type": "allow", "regex": "[a-z]" }
          ],
          "enabledBorder": {
            "borderWidth": 2,
            "borderColor": "#111111",
            "borderType": {
              "value": "outlineDashedInputBorder",
              "dashPattern": [4, 2]
            }
          }
        }
        """)

        #expect(props.initialValue == .value("hello"))
        #expect(props.maxLines == .value(4))
        #expect(props.minLines == .value(2))
        #expect(props.maxLength == .value(12))
        #expect(props.debounceValue == .value(250))
        #expect(props.labelText == .value("Name"))
        #expect(props.hintText == .value("Enter name"))
        #expect(props.validationRules?.count == 3)
        #expect(props.inputFormatters?.count == 1)
        #expect(props.enabledBorder?.borderWidth == .value(2))
        #expect(props.enabledBorder?.borderColor == .value("#111111"))
        #expect(props.enabledBorder?.borderType?.value == "outlineDashedInputBorder")
        #expect(props.enabledBorder?.borderType?.dashPattern == [4, 2])
    }

    @Test("video player props decode playback defaults")
    func videoPlayerPropsDecodePlaybackDefaults() throws {
        let props: VideoPlayerProps = try decode("""
        {
          "videoUrl": "https://example.com/video.mp4",
          "showControls": false,
          "aspectRatio": 1.77,
          "autoPlay": false,
          "looping": true
        }
        """)

        #expect(props.videoURL == .string("https://example.com/video.mp4"))
        #expect(props.showControls == .value(false))
        #expect(props.aspectRatio == .value(1.77))
        #expect(props.autoPlay == .value(false))
        #expect(props.looping == .value(true))
    }

    @MainActor
    @Test("story props decode indicator and playback settings")
    func storyPropsDecodeIndicatorAndPlaybackSettings() throws {
        let props: StoryProps = try decode("""
        {
          "dataSource": "${state.items}",
          "initialIndex": 2,
          "restartOnCompleted": true,
          "duration": 4500,
          "indicator": {
            "activeColor": "#ffffff",
            "backgroundCompletedColor": "#eeeeee",
            "backgroundDisabledColor": "#111111",
            "height": 6,
            "borderRadius": 8,
            "horizontalGap": 10
          }
        }
        """)

        #expect(props.dataSource == .string("${state.items}"))
        #expect(props.initialIndex == .value(2))
        #expect(props.restartOnCompleted == .value(true))
        #expect(props.duration == .value(4500))
        #expect(props.indicator?.height == 6)
        #expect(props.indicator?.borderRadius == 8)
        #expect(props.indicator?.horizontalGap == 10)
    }

    @MainActor
    @Test("story playback coordinator advances and repeats")
    func storyPlaybackCoordinatorAdvancesAndRepeats() async throws {
        let coordinator = StoryPlaybackCoordinator(
            pageCount: 2,
            initialIndex: 5,
            repeatOnCompleted: true,
            defaultDuration: 1.0,
            onCompleted: nil,
            onPreviousCompleted: nil,
            onStoryChanged: nil
        )

        #expect(coordinator.currentIndex == 1)

        coordinator.moveToPrevious()
        #expect(coordinator.currentIndex == 0)

        coordinator.confirmNoVideoDetected(for: coordinator.generation)
        try await Task.sleep(for: .milliseconds(20))
        coordinator.tick(delta: 1.0)
        #expect(coordinator.currentIndex == 1)

        coordinator.confirmNoVideoDetected(for: coordinator.generation)
        try await Task.sleep(for: .milliseconds(20))
        coordinator.tick(delta: 1.0)
        #expect(coordinator.currentIndex == 0)
    }

    @MainActor
    @Test("story playback coordinator waits for video and follows player progress")
    func storyPlaybackCoordinatorTracksVideoProgress() {
        let coordinator = StoryPlaybackCoordinator(
            pageCount: 1,
            initialIndex: 0,
            repeatOnCompleted: false,
            defaultDuration: 3.0,
            onCompleted: nil,
            onPreviousCompleted: nil,
            onStoryChanged: nil
        )
        let player = AVPlayer()

        coordinator.registerVideoLoading(for: coordinator.generation)
        #expect(coordinator.mode == StoryPlaybackCoordinator.Mode.detectingVideo)

        coordinator.registerVideo(player: player, duration: 5.0, autoPlay: false, generation: coordinator.generation)
        #expect(coordinator.mode == .video)
        #expect(abs(coordinator.progress - 0) < 0.0001)
    }

    @Test("story video playback bundle uses queue player only when looping")
    func storyVideoPlaybackBundleCreatesExpectedPlayerTypes() throws {
        let url = try #require(URL(string: "https://example.com/story.mp4"))

        let nonLooping = StoryVideoPlaybackBundle.make(url: url, looping: false)
        #expect(type(of: nonLooping.player) == AVPlayer.self)
        #expect(nonLooping.looper == nil)

        let looping = StoryVideoPlaybackBundle.make(url: url, looping: true)
        #expect(looping.player is AVQueuePlayer)
        #expect(looping.looper != nil)
    }

    @Test("video playback bundle uses queue player only when looping")
    func videoPlaybackBundleCreatesExpectedPlayerTypes() throws {
        let url = try #require(URL(string: "https://example.com/video.mp4"))

        let nonLooping = DigiaVideoPlaybackBundle.make(url: url, looping: false)
        #expect(type(of: nonLooping.player) == AVPlayer.self)
        #expect(nonLooping.looper == nil)

        let looping = DigiaVideoPlaybackBundle.make(url: url, looping: true)
        #expect(looping.player is AVQueuePlayer)
        #expect(looping.looper != nil)
    }

    @MainActor
    @Test("video player model rejects unsupported url schemes")
    func videoPlayerModelRejectsUnsupportedSchemes() async {
        let model = DigiaVideoPlayerModel()
        await model.load(urlString: "ftp://example.com/video.mp4", preferredAspectRatio: nil, looping: false)

        #expect(model.player == nil)
        #expect(model.errorMessage == "Unsupported video URL")
        #expect(abs(model.aspectRatio - (16.0 / 9.0)) < 0.0001)
    }
}

private func decode<T: Decodable>(_ json: String, as type: T.Type = T.self) throws -> T {
    try JSONDecoder().decode(T.self, from: Data(json.utf8))
}

import SwiftUI
import UIKit

@MainActor
final class VWImage: VirtualLeafStatelessWidget<ImageProps> {
    static func shouldStretchToFillFrame(
        fit: String?,
        hasExplicitWidth: Bool,
        hasExplicitHeight: Bool
    ) -> Bool {
        fit?.lowercased() == "fill" && hasExplicitWidth && hasExplicitHeight
    }

    override func render(_ payload: RenderPayload) -> AnyView {
        let explicitWidth = WidgetUtil.dimension(
            for: commonProps?.style?.width,
            raw: commonProps?.style?.widthRaw,
            payload: payload
        ).value
        let explicitHeight = WidgetUtil.dimension(
            for: commonProps?.style?.height,
            raw: commonProps?.style?.heightRaw,
            payload: payload
        ).value

        return AnyView(
            InternalImageView(
                props: props,
                payload: payload,
                explicitWidth: explicitWidth,
                explicitHeight: explicitHeight,
                hasExplicitWidth: commonProps?.style?.widthRaw != nil || commonProps?.style?.width != nil,
                hasExplicitHeight: commonProps?.style?.heightRaw != nil || commonProps?.style?.height != nil
            )
        )
    }
}

private struct InternalImageView: View {
    let props: ImageProps
    let payload: RenderPayload
    let explicitWidth: CGFloat?
    let explicitHeight: CGFloat?
    let hasExplicitWidth: Bool
    let hasExplicitHeight: Bool

    @State private var loadFailed = false
    @State private var intrinsicAspectRatio: CGFloat?

    // When images are revisited (e.g. story loops), SwiftUI may recreate the view,
    // which resets `@State`. Cache intrinsic aspect ratios by source to avoid
    // transient/persistent 1:1 fallback that appears as "stretched" content.
    @MainActor private static var aspectRatioCache: [String: CGFloat] = [:]

    var body: some View {
        let source = resolvedSource()
        let alignment = To.alignment(props.alignment) ?? .center

        // SwiftUI's GeometryReader-based approach was causing the image to
        // collapse to ~0 height under shrink-wrapped stacks. Instead, size the
        // image via aspect ratio (from props or intrinsic size when loaded),
        // similar to Flutter's default behavior.
        configured(source: source, alignment: alignment)
            .opacity(payload.eval(props.opacity) ?? 1)
            .onAppear {
                loadFailed = false
            }
            .onChange(of: source) { _ in
                loadFailed = false
                intrinsicAspectRatio = nil
            }
    }

    private func resolvedSource() -> String? {
        payload.eval(props.imageSrc) ?? payload.eval(props.src?.imageSrc)
    }

    private func configured(
        source: String?,
        alignment: Alignment
    ) -> AnyView {
        let fit = props.fit?.lowercased() ?? "none"
        let hasFixedFrame = explicitWidth != nil || explicitHeight != nil
        let explicitAspect = payload.eval(props.aspectRatio).map { CGFloat($0) }
        let cachedAspect = source.flatMap { Self.aspectRatioCache[$0] }
        let aspect = explicitAspect ?? intrinsicAspectRatio ?? cachedAspect
        let shouldUseFillFrame = VWImage.shouldStretchToFillFrame(
            fit: fit,
            hasExplicitWidth: hasExplicitWidth,
            hasExplicitHeight: hasExplicitHeight
        )
        let resolvedFrame = resolvedFrame(aspect: aspect)
        let contentMode: ContentMode = (fit == "cover") ? .fill : .fit
        let shouldClip = (fit == "cover" || fit == "fitwidth" || fit == "fitheight" || fit == "none" || fit == "fill")

        var current: AnyView = baseImage(source: source)

        if shouldUseFillFrame {
            current = applyExplicitFrame(
                to: current,
                alignment: alignment,
                width: resolvedFrame.width,
                height: resolvedFrame.height
            )
        } else if hasFixedFrame {
            let aspectSizedCurrent: AnyView
            if let aspect {
                aspectSizedCurrent = AnyView(current.aspectRatio(aspect, contentMode: contentMode))
            } else {
                aspectSizedCurrent = AnyView(current.aspectRatio(contentMode: contentMode))
            }

            current = applyExplicitFrame(
                to: aspectSizedCurrent,
                alignment: alignment,
                width: resolvedFrame.width,
                height: resolvedFrame.height
            )
        } else if let aspect {
            current = AnyView(
                current.aspectRatio(aspect, contentMode: contentMode)
            )
        } else {
            current = AnyView(current.aspectRatio(contentMode: contentMode))
        }

        if !hasFixedFrame {
            current = AnyView(
                current.frame(
                    maxWidth: hasExplicitWidth ? .infinity : nil,
                    maxHeight: hasExplicitHeight ? .infinity : nil,
                    alignment: alignment
                )
            )
        }

        if shouldClip {
            current = AnyView(current.clipped())
        }

        return current
    }

    private func applyExplicitFrame(
        to view: AnyView,
        alignment: Alignment,
        width: CGFloat?,
        height: CGFloat?
    ) -> AnyView {
        var current = AnyView(
            view.frame(
                width: width,
                height: height,
                alignment: alignment
            )
        )

        current = AnyView(
            current.frame(
                maxWidth: hasExplicitWidth && explicitWidth == nil ? .infinity : nil,
                maxHeight: hasExplicitHeight && explicitHeight == nil ? .infinity : nil,
                alignment: alignment
            )
        )

        return current
    }

    private func resolvedFrame(aspect: CGFloat?) -> (width: CGFloat?, height: CGFloat?) {
        guard let aspect, aspect.isFinite, aspect > 0 else {
            return (explicitWidth, explicitHeight)
        }

        if let explicitWidth, let explicitHeight {
            return (explicitWidth, explicitHeight)
        }

        if let explicitWidth {
            return (explicitWidth, explicitWidth / aspect)
        }

        if let explicitHeight {
            return (explicitHeight * aspect, explicitHeight)
        }

        return (nil, nil)
    }

    private func baseImage(source: String?) -> AnyView {
        guard let source, !source.isEmpty else {
            return placeholderView()
        }

        if loadFailed {
            return errorView()
        }

        let tintColor = resolvedTintColor(for: source)

        if source.hasPrefix("http"), let url = URL(string: source) {
            return AnyView(remoteImageView(url: url, source: source, tintColor: tintColor))
        }

        if let resourceURL = bundleResourceURL(for: source) {
            return AnyView(remoteImageView(url: resourceURL, source: source, tintColor: tintColor))
        }

        if let tintColor {
            return AnyView(
                Image(source, bundle: DigiaResourceBundle.module)
                    .renderingMode(.template)
                    .resizable()
                    .foregroundStyle(tintColor)
            )
        }

        return AnyView(Image(source, bundle: DigiaResourceBundle.module).resizable())
    }

    @ViewBuilder
    private func placeholderContent() -> some View {
        switch props.placeholder?.lowercased() {
        case "asset":
            if let src = props.placeholderSrc, !src.isEmpty {
                if let resourceURL = bundleResourceURL(for: src) {
                    DigiaCachedImageView(url: resourceURL)
                } else {
                    Image(src, bundle: DigiaResourceBundle.module).resizable()
                }
            } else {
                Rectangle().fill(Color.clear)
            }
        case "network":
            if let src = props.placeholderSrc, let url = URL(string: src), src.hasPrefix("http") {
                DigiaCachedImageView(url: url)
            } else {
                Rectangle().fill(Color.clear)
            }
        case "blurhash":
            Rectangle().fill(Color.gray.opacity(0.2))
        case "lottie":
            Rectangle().fill(Color.clear)
        default:
            Rectangle().fill(Color.clear)
        }
    }

    private func placeholderView() -> AnyView {
        AnyView(placeholderContent())
    }

    private func errorView() -> AnyView {
        if let errorSrc = props.errorImage?.errorSrc, !errorSrc.isEmpty {
            let tintColor = resolvedTintColor(for: errorSrc)
            if errorSrc.hasPrefix("http"), let url = URL(string: errorSrc) {
                return AnyView(DigiaCachedImageView(url: url, tintColor: tintColor))
            }
            if let resourceURL = bundleResourceURL(for: errorSrc) {
                return AnyView(DigiaCachedImageView(url: resourceURL, tintColor: tintColor))
            }
            if let tintColor {
                return AnyView(
                    Image(errorSrc, bundle: DigiaResourceBundle.module)
                        .renderingMode(.template)
                        .resizable()
                        .foregroundStyle(tintColor)
                )
            }
            return AnyView(Image(errorSrc, bundle: DigiaResourceBundle.module).resizable())
        }
        if props.errorImage?.errorEnabled == false {
            return AnyView(EmptyView())
        }
        let fallbackColor = payload.evalColor(props.svgColor) ?? .secondary
        return AnyView(
            Image(systemName: "exclamationmark.triangle")
                .resizable()
                .scaledToFit()
                .foregroundStyle(fallbackColor)
        )
    }

    private func bundleResourceURL(for source: String) -> URL? {
        let normalized = source.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalized.isEmpty else { return nil }

        let nsPath = normalized as NSString
        let fileExtension = nsPath.pathExtension.isEmpty ? nil : nsPath.pathExtension
        let resourceName = fileExtension == nil ? normalized : nsPath.deletingPathExtension
        let subdirectory = nsPath.deletingLastPathComponent

        return DigiaResourceBundle.module.url(
            forResource: resourceName,
            withExtension: fileExtension,
            subdirectory: subdirectory == "." ? nil : subdirectory
        )
    }

    private func resolvedTintColor(for source: String) -> Color? {
        guard let tintColor = payload.evalColor(props.svgColor) else { return nil }
        if source.hasPrefix("http") || source.contains(".") {
            return isSVGSource(source) ? tintColor : nil
        }
        return tintColor
    }

    private func isSVGSource(_ source: String) -> Bool {
        let trimmed = source.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return false }

        if let url = URL(string: trimmed), let pathExtension = url.pathExtension.nonEmpty {
            return pathExtension.caseInsensitiveCompare("svg") == .orderedSame
        }

        let fileExtension = (trimmed as NSString).pathExtension
        return fileExtension.caseInsensitiveCompare("svg") == .orderedSame
    }

    private func remoteImageView(url: URL, source: String, tintColor: Color?) -> some View {
        DigiaCachedImageView(
            url: url,
            tintColor: tintColor,
            onSuccess: { image in
                loadFailed = false
                let aspect = image.size.height > 0 ? image.size.width / image.size.height : nil
                guard let aspect, aspect.isFinite, aspect > 0 else { return }
                intrinsicAspectRatio = aspect
                Self.aspectRatioCache[source] = aspect
            },
            onFailure: {
                loadFailed = true
            }
        )
    }
}

private extension String {
    var nonEmpty: String? {
        isEmpty ? nil : self
    }
}

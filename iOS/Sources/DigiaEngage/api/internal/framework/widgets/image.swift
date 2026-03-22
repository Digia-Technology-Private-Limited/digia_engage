import DigiaExpr
import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

@MainActor
final class VWImage: VirtualLeafStatelessWidget<ImageProps> {
    override func render(_ payload: RenderPayload) -> AnyView {
        AnyView(InternalImageView(props: props, payload: payload))
    }
}

private struct InternalImageView: View {
    let props: ImageProps
    let payload: RenderPayload

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
        let explicitAspect = payload.eval(props.aspectRatio).map { CGFloat($0) }
        let cachedAspect = source.flatMap { Self.aspectRatioCache[$0] }
        let aspect = explicitAspect ?? intrinsicAspectRatio ?? cachedAspect

        let contentMode: ContentMode = (fit == "cover") ? .fill : .fit
        let shouldClip = (fit == "cover" || fit == "fitwidth" || fit == "fitheight" || fit == "none")

        var current: AnyView = baseImage(source: source)

        if let aspect {
            current = AnyView(current.aspectRatio(aspect, contentMode: contentMode))
        } else {
            current = AnyView(current.aspectRatio(contentMode: contentMode))
        }

        current = AnyView(
            current
                .frame(maxWidth: .infinity, alignment: alignment)
        )

        if shouldClip {
            current = AnyView(current.clipped())
        }

        return current
    }

    private func baseImage(source: String?) -> AnyView {
        guard let source, !source.isEmpty else {
            return placeholderView()
        }

        if loadFailed {
            return errorView()
        }

        if source.hasPrefix("http"), let url = URL(string: source) {
            return AnyView(remoteImageView(url: url))
        }

        if let resourceURL = bundleResourceURL(for: source) {
            return AnyView(remoteImageView(url: resourceURL))
        }

        if let svgColor = payload.evalColor(props.svgColor) {
            return AnyView(
                Image(source, bundle: .module)
                    .renderingMode(.template)
                    .resizable()
                    .foregroundStyle(svgColor)
            )
        }

        return AnyView(Image(source, bundle: .module).resizable())
    }

    @ViewBuilder
    private func placeholderContent() -> some View {
        switch props.placeholder?.lowercased() {
        case "asset":
            if let src = props.placeholderSrc, !src.isEmpty {
                if let resourceURL = bundleResourceURL(for: src) {
                    AsyncImage(url: resourceURL) { phase in
                        switch phase {
                        case .success(let image):
                            image.resizable()
                        default:
                            Rectangle().fill(Color.clear)
                        }
                    }
                } else {
                    Image(src, bundle: .module).resizable()
                }
            } else {
                Rectangle().fill(Color.clear)
            }
        case "network":
            if let src = props.placeholderSrc, let url = URL(string: src), src.hasPrefix("http") {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable()
                    default:
                        Rectangle().fill(Color.clear)
                    }
                }
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
            if let resourceURL = bundleResourceURL(for: errorSrc) {
                return AnyView(remoteImageView(url: resourceURL))
            }
            return AnyView(Image(errorSrc, bundle: .module).resizable())
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

    private func deviceScale() -> Double {
#if canImport(UIKit)
        return UIScreen.main.scale
#else
        return 1
#endif
    }

    private func bundleResourceURL(for source: String) -> URL? {
        let normalized = source.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalized.isEmpty else { return nil }

        let nsPath = normalized as NSString
        let fileExtension = nsPath.pathExtension.isEmpty ? nil : nsPath.pathExtension
        let resourceName = fileExtension == nil ? normalized : nsPath.deletingPathExtension
        let subdirectory = nsPath.deletingLastPathComponent

        return Bundle.module.url(
            forResource: resourceName,
            withExtension: fileExtension,
            subdirectory: subdirectory == "." ? nil : subdirectory
        )
    }

    private func remoteImageView(url: URL) -> some View {
        AsyncImage(url: url) { phase in
            switch phase {
            case .success(let image):
                image
                    .resizable()
                    .onAppear {
                        loadFailed = false
                    }
            case .failure:
                Color.clear
                    .onAppear {
                        loadFailed = true
                    }
            default:
                Color.clear
            }
        }
    }
}

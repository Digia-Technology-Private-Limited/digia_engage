import SwiftUI

@MainActor
final class VWCircularProgressBar: VirtualLeafStatelessWidget<CircularProgressBarProps> {
    override func render(_ payload: RenderPayload) -> AnyView {
        let progress = min(max((payload.eval(props.progressValue) ?? 0) / 100.0, 0), 1)
        let size = CGFloat(payload.eval(props.size) ?? 50)
        let thickness = CGFloat(payload.eval(props.thickness) ?? 5)
        let tint = payload.evalColor(props.indicatorColor) ?? .blue
        let background = payload.evalColor(props.bgColor) ?? .clear

        if props.type == "determinate" {
            return AnyView(
                DigiaDeterminateCircularBar(
                    progress: progress,
                    size: size,
                    thickness: thickness,
                    tint: tint,
                    background: background,
                    animate: props.animation ?? false
                )
            )
        }

        return AnyView(
            DigiaIndeterminateCircularBar(
                size: size,
                thickness: thickness,
                tint: tint,
                background: background
            )
        )
    }
}

package com.digia.digiaui.framework.widgets

import LocalUIResources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.digia.digiaui.framework.*
import com.digia.digiaui.framework.base.VirtualLeafNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.models.*
import com.digia.digiaui.framework.utils.JsonLike

/** Avatar properties matching the Flutter schema */
data class AvatarProps(
        val bgColor: ExprOr<String>? = null,
        val childType: String = "image",
        val text: TextProps? = null,
        val image: ImageProps? = null,
        val shape: AvatarShapeProps? = null
) {
    companion object {
        fun fromJson(json: JsonLike): AvatarProps {
            return AvatarProps(
                    bgColor = ExprOr.fromValue(json["bgColor"]),
                    childType = (json["_childType"] as? String) ?: "image",
                    text = (json["text"] as? JsonLike)?.let { TextProps.fromJson(it) },
                    image = (json["image"] as? JsonLike)?.let { ImageProps.fromJson(it) },
                    shape = (json["shape"] as? JsonLike)?.let { AvatarShapeProps.fromJson(it) }
            )
        }
    }
}

/** Avatar shape properties */
data class AvatarShapeProps(
        val value: String = "circle",
        val radius: Double = 16.0,
        val side: Double = 32.0,
        val cornerRadius: String? = "8"
) {
    companion object {
        fun fromJson(json: JsonLike): AvatarShapeProps {
            return AvatarShapeProps(
                    value = (json["value"] as? String) ?: "circle",
                    radius = (json["radius"] as? Number)?.toDouble() ?: 16.0,
                    side = (json["side"] as? Number)?.toDouble() ?: 32.0,
                    cornerRadius = json["cornerRadius"] as? String ?: "8"
            )
        }
    }
}

/** Virtual Widget for Avatar */
class VWAvatar(
        refName: String?,
        commonProps: CommonProps?,
        parent: VirtualNode?,
        parentProps: Props? = null,
        props: AvatarProps
) :
        VirtualLeafNode<AvatarProps>(
                props = props,
                commonProps = commonProps,
                parent = parent,
                refName = refName,
                parentProps = parentProps
        ) {

    @Composable
    override fun Render(payload: RenderPayload) {
        val resources = LocalUIResources.current

        // Resolve bg color
        val bgColor = payload.evalColor(props.bgColor) ?: Color.Gray

        val shapeProps = props.shape ?: AvatarShapeProps()

        val modifier = Modifier.buildModifier(payload)

        when (shapeProps.value) {
            "circle" -> RenderCircleAvatar(payload, bgColor, shapeProps, modifier)
            "square" -> RenderSquareAvatar(payload, bgColor, shapeProps, modifier)
            else -> RenderCircleAvatar(payload, bgColor, shapeProps, modifier)
        }
    }

    @Composable
    private fun RenderCircleAvatar(
            payload: RenderPayload,
            bgColor: Color,
            shapeProps: AvatarShapeProps,
            modifier: Modifier
    ) {
        val radius = shapeProps.radius.toFloat()

        Box(
                modifier = modifier.size((radius * 2).dp).clip(CircleShape).background(bgColor),
                contentAlignment = Alignment.Center
        ) { RenderAvatarChild(payload) }
    }

    @Composable
    private fun RenderSquareAvatar(
            payload: RenderPayload,
            bgColor: Color,
            shapeProps: AvatarShapeProps,
            modifier: Modifier
    ) {
        val side = shapeProps.side.toFloat()
        val cornerRadius = shapeProps.cornerRadius?.toFloatOrNull() ?: 8f

        Box(
                modifier =
                        modifier.size(side.dp)
                                .clip(RoundedCornerShape(cornerRadius.dp))
                                .background(bgColor),
                contentAlignment = Alignment.Center
        ) { RenderAvatarChild(payload) }
    }

    @Composable
    private fun RenderAvatarChild(payload: RenderPayload) {
        val childType = props.childType

        if (childType == "image" && props.image != null) {
            // Reusing logic from VWImage
            val imageProps = props.image
            val context = LocalContext.current
            val resources = LocalUIResources.current

            val imageSrc: String? =
                    imageProps.imageSrc?.let { exprOr ->
                        if (exprOr.isExpr) payload.evalExpr(exprOr)
                        else exprOr.value as? String ?: exprOr.value.toString()
                    }

            val svgColor = payload.evalColor(imageProps.svgColor?.value)

            // We'll create a simple modifier that fills the box
            val childModifier = Modifier.fillMaxSize()

            if (imageSrc.isNullOrEmpty()) {
                RenderEmpty(childModifier)
                return
            }

            // Check preloaded images first
            resources.images?.get(imageSrc)?.let {
                RenderPreloadedImage(it, childModifier, imageProps)
                return
            }

            val finalUrl = resolveImageUrl(imageSrc, imageProps.sourceType, resources)

            RenderNetworkImage(context, finalUrl, imageSrc, childModifier, imageProps, svgColor)
        } else if (props.text != null) {
            // Reusing logic from VWText used via CommonTextRender
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                CommonTextRender(
                        props = props.text,
                        payload = payload,
                        modifier = Modifier // Text renders with its own alignment/etc props
                )
            }
        }
    }
}

/** Builder function for Avatar widget */
fun avatarBuilder(
        data: VWNodeData,
        parent: VirtualNode?,
        registry: VirtualWidgetRegistry
): VirtualNode {
    return VWAvatar(
            refName = data.refName,
            commonProps = data.commonProps,
            parent = parent,
            parentProps = data.parentProps,
            props = AvatarProps.fromJson(data.props.value)
    )
}

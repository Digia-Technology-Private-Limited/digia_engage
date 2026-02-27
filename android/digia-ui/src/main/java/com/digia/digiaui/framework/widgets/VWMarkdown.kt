package com.digia.digiaui.framework.widgets

import LocalUIResources
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.View
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.actions.LocalActionExecutor
import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.base.VirtualLeafNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.expr.DefaultScopeContext
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.state.LocalStateContextProvider
import com.digia.digiaui.framework.textStyle
import com.digia.digiaui.framework.utils.JsonLike
import kotlinx.coroutines.delay
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.linkify.LinkifyPlugin

private const val LINK_ANNOTATION_TAG = "markdownUrl"

data class MarkDownProps(
	val data: ExprOr<String>? = null,
	val duration: ExprOr<Int>? = null,
	val shrinkWrap: Boolean? = null,
	val selectable: Boolean? = null,
	val animationEnabled: ExprOr<Boolean>? = null,
	val onLinkTap: ActionFlow? = null,

	val hrHeight: ExprOr<Double>? = null,
	val hrColor: ExprOr<String>? = null,

	val h1TextStyle: JsonLike? = null,
	val h2TextStyle: JsonLike? = null,
	val h3TextStyle: JsonLike? = null,
	val h4TextStyle: JsonLike? = null,
	val h5TextStyle: JsonLike? = null,
	val h6TextStyle: JsonLike? = null,
	val codeTextStyle: JsonLike? = null,
	val pTextStyle: JsonLike? = null,
	val linkTextStyle: JsonLike? = null,

	val listMarginLeft: ExprOr<Double>? = null,
	val listMarginBottom: ExprOr<Double>? = null,

	val blockSideColor: ExprOr<String>? = null,
	val blockTextColor: ExprOr<String>? = null,
	val blockSideWidth: ExprOr<Double>? = null,
	val blockPadding: Any? = null,
	val blockMargin: Any? = null,

	val prePadding: Any? = null,
	val preMargin: Any? = null,
	val preColor: ExprOr<String>? = null,
	val preBorderRadius: Any? = null,
	val preTextStyle: JsonLike? = null,
	val preLanguage: ExprOr<String>? = null,
) {
	companion object {
		fun fromJson(json: JsonLike): MarkDownProps {
			val hrConfig = json["horizontalRules"] as? JsonLike
			val h1Config = json["heading1"] as? JsonLike
			val h2Config = json["heading2"] as? JsonLike
			val h3Config = json["heading3"] as? JsonLike
			val h4Config = json["heading4"] as? JsonLike
			val h5Config = json["heading5"] as? JsonLike
			val h6Config = json["heading6"] as? JsonLike
			val preConfig = json["codeBlock"] as? JsonLike
			val linkConfig = json["link"] as? JsonLike
			val pConfig = json["paragraph"] as? JsonLike
			val blockQuoteConfig = json["blockQuote"] as? JsonLike
			val listConfig = json["list"] as? JsonLike
			val codeConfig = json["code"] as? JsonLike

			return MarkDownProps(
				duration = ExprOr.fromJson(json["duration"]),
				data = ExprOr.fromJson(json["data"]),
				animationEnabled = ExprOr.fromJson(json["animationEnabled"]),
				shrinkWrap = json["shrinkWrap"] as? Boolean,
				selectable = json["selectable"] as? Boolean,

				hrColor = ExprOr.fromJson(hrConfig?.get("color")),
				hrHeight = ExprOr.fromJson(hrConfig?.get("height")),

				h1TextStyle = h1Config?.get("textStyle") as? JsonLike,
				h2TextStyle = h2Config?.get("textStyle") as? JsonLike,
				h3TextStyle = h3Config?.get("textStyle") as? JsonLike,
				h4TextStyle = h4Config?.get("textStyle") as? JsonLike,
				h5TextStyle = h5Config?.get("textStyle") as? JsonLike,
				h6TextStyle = h6Config?.get("textStyle") as? JsonLike,

				prePadding = preConfig?.get("padding"),
				preMargin = preConfig?.get("margin"),
				preColor = ExprOr.fromJson(preConfig?.get("color")),
				preBorderRadius = preConfig?.get("borderRadius"),
				preTextStyle = preConfig?.get("textStyle") as? JsonLike,
				preLanguage = ExprOr.fromJson(preConfig?.get("language")),

				onLinkTap = ActionFlow.fromJson(linkConfig?.get("onLinkTap") as? JsonLike),
				linkTextStyle = linkConfig?.get("textStyle") as? JsonLike,

				pTextStyle = pConfig?.get("textStyle") as? JsonLike,

				blockSideColor = ExprOr.fromJson(blockQuoteConfig?.get("sideColor")),
				blockTextColor = ExprOr.fromJson(blockQuoteConfig?.get("textColor")),
				blockSideWidth = ExprOr.fromJson(blockQuoteConfig?.get("sideWidth")),
				blockPadding = blockQuoteConfig?.get("padding"),
				blockMargin = blockQuoteConfig?.get("margin"),

				listMarginLeft = ExprOr.fromJson(listConfig?.get("marginLeft")),
				listMarginBottom = ExprOr.fromJson(listConfig?.get("marginBottom")),

				codeTextStyle = codeConfig?.get("textStyle") as? JsonLike,
			)
		}
	}
}

class VWMarkdown(
	refName: String? = null,
	commonProps: CommonProps? = null,
	parent: VirtualNode? = null,
	parentProps: Props? = null,
	props: MarkDownProps,
) : VirtualLeafNode<MarkDownProps>(
	props = props,
	commonProps = commonProps,
	parent = parent,
	refName = refName,
	parentProps = parentProps,
) {

	@Composable
	override fun Render(payload: RenderPayload) {
		val context = androidx.compose.ui.platform.LocalContext.current
		val actionExecutor = LocalActionExecutor.current
		val stateContext = LocalStateContextProvider.current
		val resources = LocalUIResources.current

		val fullText = payload.evalExpr(props.data) ?: ""
		val animationEnabled = payload.evalExpr(props.animationEnabled) ?: true
		val durationMs = payload.evalExpr(props.duration) ?: 20

		var visibleText by remember(fullText, animationEnabled, durationMs) {
			mutableStateOf(if (animationEnabled) "" else fullText)
		}

		LaunchedEffect(fullText, animationEnabled, durationMs) {
			if (!animationEnabled) {
				visibleText = fullText
				return@LaunchedEffect
			}

			visibleText = ""
			var charIndex = 0
			while (charIndex < fullText.length) {
				charIndex++
				visibleText = fullText.substring(0, charIndex)
				delay(durationMs.toLong().coerceAtLeast(1))
			}
		}

		val selectable = props.selectable ?: true
		val linkStyle = payload.textStyle(props.linkTextStyle, null)?.toSpanStyle()
			?: SpanStyle(color = Color(0xff0969da), textDecoration = TextDecoration.Underline)

		// Markwon instance (remembered)
		val markwon = remember {
			Markwon.builder(context)
				.usePlugin(StrikethroughPlugin.create())
				.usePlugin(TablePlugin.create(context))
				.usePlugin(LinkifyPlugin.create())
				.build()
		}

		val onLinkClick: (String) -> Unit = { url ->
			payload.executeAction(
				context = context,
				actionFlow = props.onLinkTap,
				actionExecutor = actionExecutor,
				stateContext = stateContext,
				resourcesProvider = resources,
				incomingScopeContext = DefaultScopeContext(variables = mapOf(LINK_ANNOTATION_TAG to url)),
			)
		}

		AndroidView(
			modifier = Modifier.buildModifier(payload),
			factory = { ctx ->
				TextView(ctx).apply {
					setTextIsSelectable(selectable)
					movementMethod = LinkMovementMethod.getInstance()
					// Let Markwon style be based on TextView settings
//					setTextColor(MaterialTheme.colorScheme.onSurface.toArgbCompat())
//					textSize = MaterialTheme.typography.bodyMedium.fontSize.value
					setLineSpacing(0f, 1.2f)
					linksClickable = true
				}
			},
			update = { tv ->
				tv.setTextIsSelectable(selectable)
				markwon.setMarkdown(tv, visibleText)
				tv.replaceUrlSpans(
					linkColor = linkStyle.color,
					onClick = onLinkClick,
				)
			},
		)
	}
}

private fun TextView.replaceUrlSpans(
	linkColor: Color,
	onClick: (String) -> Unit,
) {
	val currentText = text
	if (currentText !is Spanned) return

	val spannable = SpannableString(currentText)
	val urlSpans = spannable.getSpans(0, spannable.length, URLSpan::class.java)
	if (urlSpans.isEmpty()) return

	urlSpans.forEach { span ->
		val start = spannable.getSpanStart(span)
		val end = spannable.getSpanEnd(span)
		val flags = spannable.getSpanFlags(span)
		val url = span.url

		spannable.removeSpan(span)
		spannable.setSpan(
			object : URLSpan(url) {
				override fun onClick(widget: View) {
					onClick(url)
				}
			},
			start,
			end,
			flags,
		)
	}

	setLinkTextColor(android.graphics.Color.argb(
		(linkColor.alpha * 255).toInt(),
		(linkColor.red * 255).toInt(),
		(linkColor.green * 255).toInt(),
		(linkColor.blue * 255).toInt(),
	))
	text = spannable
}

private fun Color.toArgbCompat(): Int = android.graphics.Color.argb(
	(alpha * 255).toInt(),
	(red * 255).toInt(),
	(green * 255).toInt(),
	(blue * 255).toInt(),
)

fun markdownBuilder(data: VWNodeData, parent: VirtualNode?, registry: VirtualWidgetRegistry): VirtualNode {
	return VWMarkdown(
		refName = data.refName,
		commonProps = data.commonProps,
		parent = parent,
		parentProps = data.parentProps,
		props = MarkDownProps.fromJson(data.props.value),
	)
}

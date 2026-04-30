package com.digia.digiaui.framework.actions.showPip

import android.content.Context
import com.digia.digiaui.framework.actions.ActionExecutor
import com.digia.digiaui.framework.actions.base.Action
import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.actions.base.ActionId
import com.digia.digiaui.framework.actions.base.ActionProcessor
import com.digia.digiaui.framework.actions.base.ActionType
import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.framework.models.ExprOr
import androidx.compose.ui.graphics.Color
import com.digia.digiaui.framework.pip.PipDragBounds
import com.digia.digiaui.framework.pip.PipPosition
import com.digia.digiaui.framework.pip.PipScreenFilter
import com.digia.digiaui.framework.state.StateContext
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.init.DigiaUIManager
import com.digia.digiaui.utils.asSafe

/**
 * Action: SHOW_PIP (Picture-in-Picture)
 *
 * Video mode — renders an inline video player, no component needed:
 * {
 *   "type": "SHOW_PIP",
 *   "videoUrl": "https://example.com/clip.mp4",
 *   "width": 200,
 *   "height": 120,
 *   "startX": 0.7,
 *   "startY": 0.1,
 *   "cornerRadius": 12,
 *   "showClose": true,
 *   "autoPlay": true,
 *   "looping": false,
 *   "muted": false
 * }
 *
 * Component mode — renders a registered virtual component:
 * {
 *   "type": "SHOW_PIP",
 *   "componentId": "pip_card",
 *   "args": { … },
 *   "width": 200,
 *   "height": 120,
 *   "startX": 0.7,
 *   "startY": 0.1
 * }
 */
data class ShowPipAction(
    override var actionId: ActionId? = null,
    override var disableActionIf: ExprOr<Boolean>? = null,
    val componentId: String = "",
    val args: JsonLike? = null,
    val videoUrl: String? = null,
    val widthDp: Float = 200f,
    val heightDp: Float = 120f,
    val startX: Float = 0.7f,
    val startY: Float = 0.1f,
    val cornerRadiusDp: Float = 12f,
    val backgroundColorHex: String = "#000000",
    val showClose: Boolean = true,
    val expandable: Boolean = true,
    val autoPlay: Boolean = true,
    val looping: Boolean = false,
    val muted: Boolean = false,
    val animationDurationMs: Int = 300,
    // Fields synced with PipManager — previously only parsed in DUIFactory
    val position: String? = null,               // "br"|"bl"|"tr"|"tl"|"c"
    val delayMs: Long = 0L,
    val autoDismissMs: Long = 0L,
    val screenFilter: JsonLike? = null,         // { type, screens }
    val closeOnScreenChange: Boolean = false,
    val closeOnNavigation: Boolean = false,
    val dragBounds: JsonLike? = null,           // { minX, maxX, minY, maxY }
    val waitForResult: Boolean = false,
    val onFinish: ActionFlow? = null,
) : Action {

    override val actionType = ActionType.SHOW_PIP

    override fun toJson(): JsonLike = mapOf(
        "type"                to actionType.value,
        "componentId"         to componentId,
        "args"                to args,
        "videoUrl"            to videoUrl,
        "width"               to widthDp,
        "height"              to heightDp,
        "startX"              to startX,
        "startY"              to startY,
        "cornerRadius"        to cornerRadiusDp,
        "backgroundColor"     to backgroundColorHex,
        "showClose"           to showClose,
        "expandable"          to expandable,
        "autoPlay"            to autoPlay,
        "looping"             to looping,
        "muted"               to muted,
        "animationDurationMs" to animationDurationMs,
        "position"            to position,
        "delayMs"             to delayMs,
        "autoDismissMs"       to autoDismissMs,
        "screenFilter"        to screenFilter,
        "closeOnScreenChange" to closeOnScreenChange,
        "closeOnNavigation"   to closeOnNavigation,
        "dragBounds"          to dragBounds,
        "waitForResult"       to waitForResult,
        "onFinish"            to onFinish?.toJson(),
    )

    companion object {
        fun fromJson(json: JsonLike) = ShowPipAction(
            componentId         = json["componentId"] as? String ?: "",
            args                = asSafe<JsonLike>(json["args"]),
            videoUrl            = json["videoUrl"] as? String,
            widthDp             = (json["width"] as? Number)?.toFloat() ?: 200f,
            heightDp            = (json["height"] as? Number)?.toFloat() ?: 120f,
            startX              = (json["startX"] as? Number)?.toFloat() ?: 0.7f,
            startY              = (json["startY"] as? Number)?.toFloat() ?: 0.1f,
            cornerRadiusDp      = (json["cornerRadius"] as? Number)?.toFloat() ?: 12f,
            backgroundColorHex  = json["backgroundColor"] as? String ?: "#000000",
            showClose           = json["showClose"] as? Boolean ?: true,
            expandable          = json["expandable"] as? Boolean ?: true,
            autoPlay            = json["autoPlay"] as? Boolean ?: true,
            looping             = json["looping"] as? Boolean ?: false,
            muted               = json["muted"] as? Boolean ?: false,
            animationDurationMs = (json["animationDurationMs"] as? Number)?.toInt() ?: 300,
            position            = json["position"] as? String,
            delayMs             = (json["delayMs"] as? Number)?.toLong() ?: 0L,
            autoDismissMs       = (json["autoDismissMs"] as? Number)?.toLong() ?: 0L,
            screenFilter        = asSafe<JsonLike>(json["screenFilter"]),
            closeOnScreenChange = json["closeOnScreenChange"] as? Boolean ?: false,
            closeOnNavigation   = json["closeOnNavigation"] as? Boolean ?: false,
            dragBounds          = asSafe<JsonLike>(json["dragBounds"]),
            waitForResult       = json["waitForResult"] as? Boolean ?: false,
            onFinish            = asSafe<JsonLike>(json["onFinish"])?.let { ActionFlow.fromJson(it) },
        )
    }
}

class ShowPipProcessor : ActionProcessor<ShowPipAction>() {
    override suspend fun execute(
        context: Context,
        action: ShowPipAction,
        scopeContext: ScopeContext?,
        stateContext: StateContext?,
        resourcesProvider: UIResources?,
        id: String,
    ): Any? {
        val manager = DigiaUIManager.getInstance().pipManager
        if (manager == null) {
            android.util.Log.e("ShowPip", "pipManager not initialized")
            return null
        }

        val position = when (action.position?.lowercase()) {
            "br"          -> PipPosition.BOTTOM_RIGHT
            "bl"          -> PipPosition.BOTTOM_LEFT
            "tr"          -> PipPosition.TOP_RIGHT
            "tl"          -> PipPosition.TOP_LEFT
            "c", "center" -> PipPosition.CENTER
            else          -> null
        }

        val screenFilter = action.screenFilter?.let { sf ->
            val filterType = when ((sf["type"] as? String)?.lowercase()) {
                "whitelist" -> PipScreenFilter.Type.WHITELIST
                else        -> PipScreenFilter.Type.BLACKLIST
            }
            val screens = (sf["screens"] as? List<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet()
            PipScreenFilter(filterType, screens)
        }

        val dragBounds = action.dragBounds?.let { db ->
            PipDragBounds(
                minXFraction = (db["minX"] as? Number)?.toFloat() ?: 0f,
                maxXFraction = (db["maxX"] as? Number)?.toFloat() ?: 1f,
                minYFraction = (db["minY"] as? Number)?.toFloat() ?: 0f,
                maxYFraction = (db["maxY"] as? Number)?.toFloat() ?: 1f,
            )
        }

        val backgroundColor = try {
            Color(android.graphics.Color.parseColor(action.backgroundColorHex))
        } catch (_: Exception) { Color.Black }

        manager.show(
            componentId         = action.componentId,
            args                = action.args,
            videoUrl            = action.videoUrl,
            position            = position,
            startX              = action.startX,
            startY              = action.startY,
            widthDp             = action.widthDp,
            heightDp            = action.heightDp,
            cornerRadiusDp      = action.cornerRadiusDp,
            backgroundColor     = backgroundColor,
            showClose           = action.showClose,
            expandable          = action.expandable,
            autoPlay            = action.autoPlay,
            looping             = action.looping,
            muted               = action.muted,
            animationDurationMs = action.animationDurationMs,
            delayMs             = action.delayMs,
            autoDismissMs       = action.autoDismissMs,
            screenFilter        = screenFilter,
            closeOnScreenChange = action.closeOnScreenChange,
            closeOnNavigation   = action.closeOnNavigation,
            dragBounds          = dragBounds,
            onDismiss           = { result ->
                if (action.waitForResult && action.onFinish != null) {
                    val ctx = com.digia.digiaui.framework.expr.DefaultScopeContext(
                        variables = mapOf("result" to result),
                        enclosing = scopeContext,
                    )
                    ActionExecutor().execute(
                        context           = context,
                        actionFlow        = action.onFinish,
                        scopeContext      = ctx,
                        stateContext      = stateContext,
                        resourcesProvider = resourcesProvider,
                    )
                }
            },
        )
        return null
    }
}

// ─── DISMISS ─────────────────────────────────────────────────────────────────

data class DismissPipAction(
    override var actionId: ActionId? = null,
    override var disableActionIf: ExprOr<Boolean>? = null,
) : Action {
    override val actionType = ActionType.DISMISS_PIP
    override fun toJson(): JsonLike = mapOf("type" to actionType.value)
    companion object { fun fromJson(@Suppress("UNUSED_PARAMETER") json: JsonLike) = DismissPipAction() }
}

class DismissPipProcessor : ActionProcessor<DismissPipAction>() {
    override suspend fun execute(
        context: Context,
        action: DismissPipAction,
        scopeContext: ScopeContext?,
        stateContext: StateContext?,
        resourcesProvider: UIResources?,
        id: String,
    ): Any? {
        DigiaUIManager.getInstance().pipManager?.dismiss()
        return null
    }
}

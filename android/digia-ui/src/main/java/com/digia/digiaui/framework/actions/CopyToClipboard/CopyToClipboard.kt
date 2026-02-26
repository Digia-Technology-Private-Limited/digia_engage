package com.digia.digiaui.framework.actions.CopyToClipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.framework.actions.base.Action
import com.digia.digiaui.framework.actions.base.ActionId
import com.digia.digiaui.framework.actions.base.ActionProcessor
import com.digia.digiaui.framework.actions.base.ActionType
import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.state.StateContext
import com.digia.digiaui.framework.utils.JsonLike

/**
 * Copy to Clipboard Action
 *
 * Copies text to the system clipboard.
 *
 * @param message The text to copy to clipboard (can be an expression)
 */
data class CopyToClipboardAction(
    override var actionId: ActionId? = null,
    override var disableActionIf: ExprOr<Boolean>? = null,
    val message: ExprOr<String>?
) : Action {
    override val actionType = ActionType.COPY_TO_CLIPBOARD

    override fun toJson(): JsonLike =
        mapOf(
            "type" to actionType.value,
            "message" to message?.toJson()
        )

    companion object {
        fun fromJson(json: JsonLike): CopyToClipboardAction {
            return CopyToClipboardAction(
                message = ExprOr.fromValue(json["message"])
            )
        }
    }
}

/** Processor for copy to clipboard action */
class CopyToClipboardProcessor : ActionProcessor<CopyToClipboardAction>() {
    override suspend fun execute(
        context: Context,
        action: CopyToClipboardAction,
        scopeContext: ScopeContext?,
        stateContext: StateContext?,
        resourceProvider: UIResources?,
        id: String
    ): Any? {
        val message = action.message?.evaluate<String>(scopeContext)

        if (message != null && message.isNotEmpty()) {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Copied Text", message)
                clipboard.setPrimaryClip(clip)
            } catch (e: Exception) {
                throw e
            }
        }

        return null
    }
}
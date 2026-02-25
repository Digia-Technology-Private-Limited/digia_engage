package com.digia.digiaui.framework.actions.share

import android.content.Context
import android.content.Intent
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
 * Share Content Action
 *
 * Shares text content using Android's share intent.
 *
 * @param message The text message to share (can be an expression)
 * @param subject The subject/title for the share (can be an expression)
 */
data class ShareAction(
    override var actionId: ActionId? = null,
    override var disableActionIf: ExprOr<Boolean>? = null,
    val message: ExprOr<String>?,
    val subject: ExprOr<String>?
) : Action {
    override val actionType = ActionType.SHARE_CONTENT

    override fun toJson(): JsonLike =
        mapOf(
            "type" to actionType.value,
            "message" to message?.toJson(),
            "subject" to subject?.toJson()
        )

    companion object {
        fun fromJson(json: JsonLike): ShareAction {
            return ShareAction(
                message = ExprOr.fromValue(json["message"]),
                subject = ExprOr.fromValue(json["subject"])
            )
        }
    }
}

/** Processor for share content action */
class ShareProcessor : ActionProcessor<ShareAction>() {
    override suspend fun execute(
        context: Context,
        action: ShareAction,
        scopeContext: ScopeContext?,
        stateContext: StateContext?,
        resourceProvider: UIResources?,
        id: String
    ): Any? {
        // Evaluate message and subject
        val message = action.message?.evaluate<String>(scopeContext)
        val subject = action.subject?.evaluate<String>(scopeContext)

        if (message is String && message.isNotEmpty()) {
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                val chooserIntent = Intent.createChooser(shareIntent, null)
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooserIntent)

            } catch (e: Exception) {
                println("ShareAction: Failed to share content - ${e.message}")
                e.printStackTrace()
                throw e
            }
        } else {
            println("ShareAction: Message is empty or not a string")
        }
        return  null
    }

}
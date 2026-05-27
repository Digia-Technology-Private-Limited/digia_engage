package com.digia.engage.framework.actions.postmessage

import android.content.Context
import com.digia.engage.framework.UIResources
import com.digia.engage.framework.actions.base.Action
import com.digia.engage.framework.actions.base.ActionId
import com.digia.engage.framework.actions.base.ActionProcessor
import com.digia.engage.framework.actions.base.ActionType
import com.digia.engage.framework.expr.ScopeContext
import com.digia.engage.framework.message.Message
import com.digia.engage.framework.models.ExprOr
import com.digia.engage.framework.state.StateContext
import com.digia.engage.framework.utils.JsonLike
import com.digia.engage.init.DigiaUIManager

/**
 * Post Message Action
 *
 * Sends a message through the message bus system.
 *
 * @param name The message name/channel
 * @param payload The message payload (can be an expression)
 */
data class PostMessageAction(
    override var actionId: ActionId? = null,
    override var disableActionIf: ExprOr<Boolean>? = null,
    val name: String,
    val payload: ExprOr<Any>?
) : Action {
    override val actionType = ActionType.POST_MESSAGE

    override fun toJson(): JsonLike =
        mapOf(
            "type" to actionType.value,
            "name" to name,
            "payload" to payload?.toJson()
        )

    companion object {
        fun fromJson(json: JsonLike): PostMessageAction {
            return PostMessageAction(
                name = json["name"] as String,
                payload = ExprOr.fromValue(json["payload"] ?: json["body"])
            )
        }
    }
}

/** Processor for post message action */
class PostMessageProcessor : ActionProcessor<PostMessageAction>() {
    override suspend fun execute(
        context: Context,
        action: PostMessageAction,
        scopeContext: ScopeContext?,
        stateContext: StateContext?,
        resourceProvider: UIResources?,
        id: String
    ): Any? {
        val name = action.name
        val payload = action.payload?.deepEvaluate(scopeContext)

        val messageBus = DigiaUIManager.getInstance().messageBus
        messageBus.send(Message(name = name, payload = payload, context = context))

        return null
    }
}
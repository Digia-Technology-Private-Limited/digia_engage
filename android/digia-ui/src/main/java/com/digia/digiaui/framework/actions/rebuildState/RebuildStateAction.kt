package com.digia.digiaui.framework.actions.rebuildState

import android.content.Context
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.framework.actions.base.Action
import com.digia.digiaui.framework.actions.base.ActionId
import com.digia.digiaui.framework.actions.base.ActionProcessor
import com.digia.digiaui.framework.actions.base.ActionType
import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.framework.utils.JsonUtil.Companion.tryKeys

/**
 * RebuildState Action
 *
 * Forces a rebuild of the state scope by flushing the state context.
 * This will trigger a recomposition of all widgets that depend on the state.
 */
data class RebuildStateAction(
    override var actionId: ActionId? = null,
    override var disableActionIf: ExprOr<Boolean>? = null,
    val stateContextName:String?
) : Action {
    override val actionType = ActionType.REBUILD_STATE

    override fun toJson(): JsonLike {
        return mapOf(
            "stateContextName" to stateContextName
        )
    }

    companion object {
        fun fromJson(json: JsonLike): RebuildStateAction {
            return RebuildStateAction(
                stateContextName = tryKeys(json, listOf("stateContextName"))
            )
        }
    }
}

/** RebuildState Action Processor */
class RebuildStateProcessor : ActionProcessor<RebuildStateAction>() {
    override suspend fun execute(
        context: Context,
        action: RebuildStateAction,
        scopeContext: ScopeContext?,
        stateContext: com.digia.digiaui.framework.state.StateContext?,
        resourcesProvider: UIResources?,

        id: String
    ): Any? {
        if (stateContext == null) {
            println("Warning: RebuildStateAction executed without a StateContext")
            return null
        }

         stateContext.flush()

        return null
    }
}


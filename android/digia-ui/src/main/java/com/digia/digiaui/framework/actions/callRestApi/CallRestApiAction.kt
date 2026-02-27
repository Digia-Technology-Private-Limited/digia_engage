package com.digia.digiaui.framework.actions.callRestApi

import android.content.Context
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.framework.actions.ActionExecutor
import com.digia.digiaui.framework.actions.base.Action
import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.actions.base.ActionId
import com.digia.digiaui.framework.actions.base.ActionProcessor
import com.digia.digiaui.framework.actions.base.ActionType
import com.digia.digiaui.framework.expr.DefaultScopeContext
import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.framework.utils.executeApiAction
import com.digia.digiaui.init.DigiaUIManager
import com.digia.digiaui.utils.asSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CallRestApi Action
 *
 * Makes a REST API call using the configured data source.
 * Supports dynamic parameter evaluation and state updates with response data.
 *
 * @param dataSource The data source configuration for the API call
 * @param successCondition Optional condition to determine success
 * @param onSuccess Optional action flow to execute on successful response
 * @param onError Optional action flow to execute on error
 */
data class CallRestApiAction(
    override var actionId: ActionId? = null,
    override var disableActionIf: ExprOr<Boolean>? = null,
    val dataSource: ExprOr<JsonLike>?,
    val successCondition: ExprOr<Boolean>? = null,
    val onSuccess: ActionFlow? = null,
    val onError: ActionFlow? = null
) : Action {
    override val actionType = ActionType.CALL_REST_API

    override fun toJson(): JsonLike =
        mapOf(
            "type" to actionType.value,
            "dataSource" to dataSource?.toJson(),
            "successCondition" to successCondition?.toJson(),
            "onSuccess" to onSuccess?.toJson(),
            "onError" to onError?.toJson()
        )

    companion object {
        fun fromJson(json: JsonLike): CallRestApiAction {
            return CallRestApiAction(
                dataSource = ExprOr.fromValue(json["dataSource"]),
                successCondition = ExprOr.fromValue(json["successCondition"]),
                onSuccess = ActionFlow.fromJson(asSafe<JsonLike>(json["onSuccess"])),
                onError = ActionFlow.fromJson(asSafe<JsonLike>(json["onError"]))
            )
        }
    }
}

/** Processor for call REST API action */
class CallRestApiProcessor : ActionProcessor<CallRestApiAction>() {
    private val actionExecutor = ActionExecutor()
    override suspend fun execute(
        context: Context,
        action: CallRestApiAction,
        scopeContext: ScopeContext?,
        stateContext: com.digia.digiaui.framework.state.StateContext?,
        resourcesProvider: UIResources?,

        id: String
    ) {
        // Execute API call on IO dispatcher and suspend until complete
        withContext(Dispatchers.IO) {
            try {
                // Evaluate dataSource
                val dataSource: JsonLike? = action.dataSource?.evaluate(scopeContext)
                if (dataSource == null) {
                    println("CallRestApiAction: dataSource is null")
                    return@withContext
                }

                val apiModelId = dataSource["id"] as? String ?: ""
                if (apiModelId.isEmpty()) {
                    println("CallRestApiAction: apiModelId is empty")
                    return@withContext
                }

                // Get the APIModel from DigiaUIManager config
                val apiModel = DigiaUIManager.getInstance().config.getApiDataSource(apiModelId)
                if (apiModel == null) {
                    val err = Exception("No API Selected")
                    println("CallRestApiAction: APIModel not found for ID: $apiModelId")
                    throw err
                }

                println("CallRestApiAction: Making API call with model ID: $apiModelId")

                val result = executeApiAction(
                    scopeContext = scopeContext,
                    apiModel = apiModel,
                    args = asSafe<JsonLike>(dataSource["args"])?.mapValues { (_, v) ->
                        ExprOr.fromValue(v)
                    },
                    successCondition = action.successCondition?.evaluate(scopeContext),
                    onSuccess = { response ->
                        println("CallRestApiAction: API call successful")
                        
                        if (action.onSuccess != null) {
                            actionExecutor.execute(
                                context = context,
                                actionFlow = action.onSuccess,
                                scopeContext = DefaultScopeContext(
                                    variables = mapOf("response" to response),
                                    enclosing = scopeContext
                                ),
                                stateContext = stateContext,
                                resourcesProvider = resourcesProvider
                            )
                        } else {
                            null
                        }
                    },
                    onError = { response ->
                        println("CallRestApiAction: API call error")
                        
                        if (action.onError != null) {
                            actionExecutor.execute(
                                context = context,
                                actionFlow = action.onError,
                                scopeContext = DefaultScopeContext(
                                    variables = mapOf("response" to response),
                                    enclosing = scopeContext
                                ),
                                stateContext = stateContext,
                                resourcesProvider = resourcesProvider

                            )
                        } else {
                            null
                        }
                    }
                )

                println("CallRestApiAction: API call completed, result: $result")
            } catch (e: Exception) {
                println("CallRestApiAction: API call failed: ${e.message}")
                e.printStackTrace()
                throw e
            }
        }
    }
}

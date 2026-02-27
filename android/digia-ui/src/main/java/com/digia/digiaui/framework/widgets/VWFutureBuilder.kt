package com.digia.digiaui.framework.widgets

import LocalUIResources
import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.actions.ActionExecutor
import com.digia.digiaui.framework.actions.LocalActionExecutor
import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.apiModel
import com.digia.digiaui.framework.base.VirtualCompositeNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.expr.DefaultScopeContext
import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.state.LocalStateContextProvider
import com.digia.digiaui.framework.state.StateContext
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.framework.utils.executeApiAction
import com.digia.digiaui.init.DigiaUIManager
import com.digia.digiaui.network.ApiResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import resourceApiModel


import androidx.compose.runtime.*
import com.digia.digiaui.framework.registerAllChildern
import com.digia.digiaui.utils.asSafe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext

// ----------------------------
// Async State
// ----------------------------
sealed class AsyncState<out T> {
    class Loading<out T> : AsyncState<T>()
    data class Success<out T>(val data: T) : AsyncState<T>()
    data class Error(val throwable: Throwable) : AsyncState<Nothing>()
}


// ----------------------------
// Async Controller
// ----------------------------
class AsyncController<T>(
    private var futureCreator: (suspend () -> T)? = null,
    private val coroutineContext: CoroutineContext = Dispatchers.Main
) {
    private val _state = MutableStateFlow<AsyncState<T>>(AsyncState.Loading())
    val state: StateFlow<AsyncState<T>> = _state.asStateFlow()

    private var isDirty = true


    fun  isDirty() : Boolean {
        return isDirty
    }

    fun setFutureCreator(creator: suspend () -> T) {
        futureCreator = creator
        invalidate()
    }

    fun invalidate() {
        isDirty = true
    }

    fun load(scope: CoroutineScope) {
        if (!isDirty) return
        isDirty = false

        val creator = futureCreator
        if (creator == null) {
            _state.value = AsyncState.Error(IllegalStateException("FutureCreator not set"))
            return
        }

        scope.launch(coroutineContext) {
            _state.value = AsyncState.Loading()
            try {
                val result = creator()
                _state.value = AsyncState.Success(result)
            } catch (t: Throwable) {
                _state.value = AsyncState.Error(t)
            }
        }
    }
}

// ----------------------------
// Compose AsyncBuilder
@Composable
fun <T> AsyncBuilder(
    initialData: T? = null,
    controller: AsyncController<T>,
    contentBuilder: @Composable (AsyncState<T>) -> Unit
) {
    val scope = rememberCoroutineScope()

    // Use provided controller or create local
    val asyncController = remember(controller) {
        controller
    }

    // Trigger load when controller changes
    LaunchedEffect(asyncController,asyncController.isDirty(),LocalStateContextProvider.current?.Version()) {
        asyncController.load(scope)
    }

    // Observe state
    val state by asyncController.state.collectAsState(
        initial = initialData?.let { AsyncState.Success(it) }
            ?: AsyncState.Loading()
    )

    // Pure render
    contentBuilder(state)
}



enum class FutureState {
    loading,
    completed,
    error
}

enum class FutureType {
    api
}


data class AsyncBuilderProps(
    val future: ExprOr<JsonLike>? = null,
    val controller: ExprOr<AsyncController<Any?>>? = null,
    val initialData: ExprOr<Any>? = null,
    val onSuccess: ActionFlow? = null,
    val onError: ActionFlow? = null,
) {
    companion object {
        fun fromJson(json: JsonLike): AsyncBuilderProps {
            return AsyncBuilderProps(
                future = ExprOr.fromJson(json["future"]),
                controller = ExprOr.fromJson(json["controller"]),
                initialData = ExprOr.fromJson(json["initialData"]),
                onSuccess = ActionFlow.fromJson(asSafe<JsonLike>(json["onSuccess"])),
                onError = ActionFlow.fromJson(asSafe<JsonLike>(json["onError"])),
            )
        }
    }
}


class VWAsyncBuilder(
    props: AsyncBuilderProps,
    commonProps: CommonProps?,
    parentProps: Props?,
    parent: VirtualNode?,
    slots: ((VirtualCompositeNode<AsyncBuilderProps>) -> Map<String, List<VirtualNode>>?)? = null,
    refName: String? = null
) : VirtualCompositeNode<AsyncBuilderProps>(
    props = props,
    commonProps = commonProps,
    parentProps = parentProps,
    parent = parent,
    _slots = slots,
    refName = refName
) {

    @Composable
    override fun Render(payload: RenderPayload) {
        val context = LocalContext.current
        val stateContext = LocalStateContextProvider.current
        val actionExecutor = LocalActionExecutor.current
        val resourceProvider = LocalUIResources.current

        val controller =
            payload.evalExpr<AsyncController<Any?>>(props.controller)
                ?: remember { AsyncController<Any?>() }

        val futureCreator = remember(
            props.future,
            props.onSuccess,
            props.onError,
            payload.scopeContext,
            stateContext?.version,
        ) {
            suspend {
                makeFuture(
                    props,
                    payload,
                    context,
                    actionExecutor,
                    stateContext,
                    resourceProvider = resourceProvider
                )
            }
        }

        LaunchedEffect(controller, futureCreator) {
            controller.setFutureCreator { futureCreator() }
        }

        // Invalidate controller when state version changes
        val stateVersion = stateContext?.version
        LaunchedEffect(stateVersion) {
            controller.invalidate()
        }

        val initialData= payload.evalExpr(props.initialData, decoder ={
                it->
            if (it is Map<*, *>&&it.isEmpty()) {
                return@evalExpr null
            }
            return@evalExpr null
        })

        // 👇 gate to control UI rendering
        AsyncBuilder(
            controller = controller,
            initialData =initialData,
        ) { asyncState ->

            val futureType = getFutureType(props, payload)

            val updatedPayload = payload.copyWithChainedContext(
                createExprContext(asyncState, futureType, refName)
            )

            child?.ToWidget(updatedPayload)
        }

    }

}



private fun getFutureType(
    props: AsyncBuilderProps,
    payload: RenderPayload
): FutureType? {
    val futureProps = payload.evalExpr(props.future)
    val type = futureProps?.get("futureType") as? String

    return when (type) {
        "api" -> FutureType.api
        else -> null
    }
}


private fun getFutureState(state: AsyncState<*>): FutureState =
    when (state) {
        is AsyncState.Loading -> FutureState.loading
        is AsyncState.Success -> FutureState.completed
        is AsyncState.Error -> FutureState.error
    }


private fun createExprContext(
    state: AsyncState<Any?>,
    futureType: FutureType?,
    refName: String?
): ScopeContext {

    val futureState = getFutureState(state)

    return when (futureType) {
        FutureType.api -> createApiExprContext(state, futureState, refName)
        else -> createDefaultExprContext(state, futureState, refName)
    }
}


private fun createApiExprContext(
    state: AsyncState<Any?>,
    futureState: FutureState,
    refName: String?
): ScopeContext {

    var dataKey: Any? = null
    var responseKey: Map<String, Any?>? = null

    when (state) {
        is AsyncState.Loading -> {
            dataKey = null
        }

        is AsyncState.Error -> {
            val t = state.throwable
            responseKey = mapOf(
                "error" to t.message
            )
        }

        is AsyncState.Success -> {
            val apiResp = state.data as? ApiResponse<*>
            if (apiResp != null) {
                when (apiResp) {
                    is ApiResponse.Success -> {
                        dataKey = apiResp.data
                        responseKey = mapOf(
                            "body" to apiResp.data,
                            "statusCode" to apiResp.statusCode,
                            "headers" to apiResp.headers,
                            "error" to null
                        )
                    }
                    is ApiResponse.Error -> {
                        dataKey = apiResp.body
                        responseKey = mapOf(
                            "body" to apiResp.body,
                            "statusCode" to apiResp.statusCode,
                            "headers" to emptyMap<String, Any>(),
                            "error" to apiResp.message
                        )
                    }
                }
            }
        }
    }

    val respObj = mapOf(
        "futureState" to futureState.name,
        "futureValue" to dataKey,
        "response" to responseKey
    )

    return DefaultScopeContext(
        variables = buildMap {
            putAll(respObj)
            refName?.let { put(it, respObj) }
        }
    )
}


private fun createDefaultExprContext(
    state: AsyncState<Any?>,
    futureState: FutureState,
    refName: String?
): ScopeContext {

    val respObj = buildMap<String, Any?> {
        put("futureState", futureState.name)
        when (state) {
            is AsyncState.Success -> put("futureValue", state.data)
            is AsyncState.Error -> put("error", state.throwable)
            else -> {}
        }
    }

    return DefaultScopeContext(
        variables = buildMap {
            putAll(respObj)
            refName?.let { put(it, respObj) }
        }
    )
}


private suspend fun makeFuture(
    props: AsyncBuilderProps,
    payload: RenderPayload,
    context: Context,
    actionExecutor: ActionExecutor,
    stateContext: StateContext?,
    resourceProvider: UIResources?
): Any? {

    val futureProps = payload.evalExpr(props.future)
        ?: error("Future props not provided")

    val type = getFutureType(props, payload) ?: return null

    return when (type) {
        FutureType.api -> makeApiFuture(
            futureProps = futureProps,
            payload = payload,
            context = context,
            actionExecutor = actionExecutor,
            stateContext = stateContext,
            resourceProvider = resourceProvider,
            onSuccess = props.onSuccess,
            onError = props.onError,
        )
    }
}



private suspend fun makeApiFuture(
    futureProps: JsonLike,
    payload: RenderPayload,
    context: Context,
    actionExecutor: ActionExecutor,
    stateContext: StateContext?,
    resourceProvider: UIResources?,
    onSuccess: ActionFlow?,
    onError: ActionFlow?,
): ApiResponse<Any> {

    val dataSource =asSafe<JsonLike>( futureProps["dataSource"])
    val apiId = dataSource?.get("id") as? String
        ?: error("No API Selected")

    val apiModel = DigiaUIManager.getInstance().config.getApiDataSource(apiId)
        ?: error("No API Model found for id: $apiId")

    // Extract args from dataSource
    val args = asSafe<JsonLike>(dataSource["args"])?.mapValues { (_, v) ->
        ExprOr.fromValue<Any>(v)
    }

    // Execute API call; await success/error actions BEFORE returning (so AsyncState changes only after actions complete).
    return executeApiAction(
        scopeContext = payload.scopeContext,
        apiModel = apiModel,
        args = args,
        onSuccess = { respObj ->
            if (onSuccess == null) return@executeApiAction null
            actionExecutor.execute(
                context = context,
                actionFlow = onSuccess,
                scopeContext = DefaultScopeContext(
                    variables = mapOf("response" to respObj),
                    enclosing = payload.scopeContext
                ),
                stateContext = stateContext,
                resourcesProvider = resourceProvider,
            )
        },
        onError = { respObj ->
            if (onError == null) return@executeApiAction null
            actionExecutor.execute(
                context = context,
                actionFlow = onError,
                scopeContext = DefaultScopeContext(
                    variables = mapOf("response" to respObj),
                    enclosing = payload.scopeContext
                ),
                stateContext = stateContext,
                resourcesProvider = resourceProvider,
            )
        },
    )
}



fun futureBuilder(
    data: VWNodeData,
    parent: VirtualNode?,
    registry: VirtualWidgetRegistry
): VirtualNode {

    return VWAsyncBuilder(
        refName = data.refName,
        commonProps = data.commonProps,
        parent = parent,
        parentProps = data.parentProps ?: Props.empty(),
        props = AsyncBuilderProps.fromJson(data.props.value),
        slots = {
                self ->
            registerAllChildern(data.childGroups, self, registry)
        },
    )
}
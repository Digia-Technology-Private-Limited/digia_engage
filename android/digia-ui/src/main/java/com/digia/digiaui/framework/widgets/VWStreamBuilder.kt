package com.digia.digiaui.framework.widgets

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue


import android.content.Context
import androidx.compose.ui.Modifier
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.actions.LocalActionExecutor
import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.base.VirtualCompositeNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.expr.DefaultScopeContext
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.state.LocalStateContextProvider
import com.digia.digiaui.framework.utils.JsonLike
import kotlinx.coroutines.flow.Flow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalContext
import LocalUIResources
import com.digia.digiaui.framework.registerAllChildern
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/**
 * Stream state enum matching Dart implementation
 */
enum class StreamState {
    LOADING,
    LISTENING,
    COMPLETED,
    ERROR;

    val value: String get() = name.lowercase()
}

/**
 * Async snapshot sealed class for representing stream states
 */
sealed class AsyncSnapshot<out T> {
    data class Data<T>(val value: T) : AsyncSnapshot<T>()
    data class Error(val throwable: Throwable) : AsyncSnapshot<Nothing>()
    object Loading : AsyncSnapshot<Nothing>()
    object Completed : AsyncSnapshot<Nothing>()

    val hasError get() = this is Error
    val data get() = (this as? Data)?.value
    val error get() = (this as? Error)?.throwable
}


/**
 * Internal composable stream builder
 */
@Composable
fun <T> InternalStreamBuilder(
    flow: Flow<T>,
    initialData: T? = null,
    onSuccess: ((T) -> Unit)? = null,
    onError: ((Throwable) -> Unit)? = null,
    builder: @Composable (AsyncSnapshot<T>) -> Unit
) {
    var snapshot by remember(flow) {
        mutableStateOf<AsyncSnapshot<T>>(
            initialData?.let { AsyncSnapshot.Data(it) }
                ?: AsyncSnapshot.Loading
        )
    }

    LaunchedEffect(flow) {
        flow
            .onStart {
                if (initialData == null) {
                    snapshot = AsyncSnapshot.Loading
                }
            }
            .catch { throwable ->
                snapshot = AsyncSnapshot.Error(throwable)
                onError?.invoke(throwable)
            }
            .onCompletion {
                snapshot = AsyncSnapshot.Completed
            }
            .collect { value ->
                snapshot = AsyncSnapshot.Data(value)
                onSuccess?.invoke(value)
            }
    }

    builder(snapshot)
}


/**
 * Props for VWStreamBuilder
 */
data class StreamBuilderProps(
    val controller: ExprOr<Any>?,  // Should evaluate to Flow<Any?>
    val initialData: ExprOr<Any>? = null,
    val onSuccess: ActionFlow? = null,
    val onError: ActionFlow? = null
) {
    companion object {
        fun fromJson(json: JsonLike): StreamBuilderProps {
            return StreamBuilderProps(
                controller = ExprOr.fromValue(json["controller"]),
                initialData = ExprOr.fromValue(json["initialData"]),
                onSuccess = (json["onSuccess"] as? JsonLike)?.let { ActionFlow.fromJson(it) },
                onError = (json["onError"] as? JsonLike)?.let { ActionFlow.fromJson(it) }
            )
        }
    }
}

/**
 * Virtual StreamBuilder Widget
 * 
 * Listens to a Flow/Stream and rebuilds when new data is emitted.
 * Provides stream state and value to child widgets through scope context.
 */
class VWStreamBuilder(
    refName: String? = null,
    commonProps: CommonProps? = null,
    props: StreamBuilderProps,
    parent: VirtualNode? = null,
    slots: ((VirtualCompositeNode<StreamBuilderProps>) -> Map<String, List<VirtualNode>>?)? = null,
    parentProps: Props? = null
) : VirtualCompositeNode<StreamBuilderProps>(
    props = props,
    commonProps = commonProps,
    parentProps = parentProps,
    parent = parent,
    refName = refName,
    _slots = slots
) {

    @Composable
    override fun Render(payload: RenderPayload) {
        val context = LocalContext.current
        val actionExecutor = LocalActionExecutor.current
        val stateContext = LocalStateContextProvider.current
        val resources = LocalUIResources.current

        // Evaluate controller expression to get the Flow
        val controller = payload.evalExpr(props.controller) as? Flow<Any?>

        if (controller == null) {
            Empty()
            return
        }

        // Evaluate initial data if provided
        val initialData = payload.evalExpr(props.initialData)

        // Render using InternalStreamBuilder
        InternalStreamBuilder(
            flow = controller,
            initialData = initialData,
            onSuccess = { data ->
                // Execute onSuccess action if provided
                props.onSuccess?.let {
                    val successScope = _createExprContext(
                        AsyncSnapshot.Data(data)
                    )
                    payload.executeAction(
                        context = context,
                        actionFlow = it,
                        actionExecutor = actionExecutor,
                        stateContext = stateContext,
                            resourcesProvider = resources,
                        incomingScopeContext = successScope
                    )
                }
            },
            onError = { throwable ->
                // Execute onError action if provided
                props.onError?.let {
                    payload.executeAction(
                        context = context,
                        actionFlow = it,
                        actionExecutor = actionExecutor,
                        stateContext = stateContext,
                            resourcesProvider = resources,
                        incomingScopeContext = null
                    )
                }
            }
        ) { snapshot ->
            // Create scoped context with stream state and value
            val updatedPayload = payload.copyWithChainedContext(
                _createExprContext(snapshot)
            )

            // Use key to force recomposition when snapshot changes
            key(snapshot) {
                child?.ToWidget(updatedPayload) ?: Empty()
            }
        }
    }

    /**
     * Get stream state from AsyncSnapshot
     */
    private fun _getStreamState(snapshot: AsyncSnapshot<Any?>): StreamState =
        when (snapshot) {
            is AsyncSnapshot.Loading -> StreamState.LOADING
            is AsyncSnapshot.Data -> StreamState.LISTENING
            is AsyncSnapshot.Error -> StreamState.ERROR
            is AsyncSnapshot.Completed -> StreamState.COMPLETED
        }


    /**
     * Create expression context with stream state and value
     */
    private fun _createExprContext(snapshot: AsyncSnapshot<Any?>): DefaultScopeContext {
        val streamState = _getStreamState(snapshot)

        val streamObj = buildMap<String, Any?> {
            put("streamState", streamState.value)
            put("streamValue", snapshot.data)
            if (snapshot.hasError) {
                put("error", snapshot.error)
            }
        }

        val variables = buildMap<String, Any?> {
            putAll(streamObj)
            // Add named reference if refName is provided
            refName?.let { name ->
                put(name, streamObj)
            }
        }

        return DefaultScopeContext(
            variables = variables
        )
    }

}

/**
 * Builder function for StreamBuilder widget
 */
fun streamBuilderBuilder(
    data: VWNodeData,
    parent: VirtualNode?,
    registry: VirtualWidgetRegistry
): VirtualNode {


    return VWStreamBuilder(
        refName = data.refName,
        commonProps = data.commonProps,
        parent = parent,
        parentProps = data.parentProps,
        props = StreamBuilderProps.fromJson(data.props.value),
        slots = {
                self ->
            registerAllChildern(data.childGroups, self, registry)
        },
    )
}
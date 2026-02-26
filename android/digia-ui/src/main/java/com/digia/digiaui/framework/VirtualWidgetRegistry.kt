package com.digia.digiaui.framework

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.digia.digiaui.framework.base.VirtualLeafNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWComponentData
import com.digia.digiaui.framework.models.VWData
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.models.VWStateData
import com.digia.digiaui.framework.state.VWStateContainer
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.framework.widgets.dummyBuilder

/** Type alias for widget builder functions */
typealias VirtualWidgetBuilder = (
    data: VWNodeData,
    parent: VirtualNode?,
    registry: VirtualWidgetRegistry
) -> VirtualNode

/** Type alias for component builder functions */
typealias ComponentBuilder = @Composable (String, Map<String, Any?>?) -> Unit

/**
 * Virtual widget registry - manages widget types and builders Mirrors Flutter VirtualWidgetRegistry
 */
interface VirtualWidgetRegistry {
    fun createWidget(data: VWData, parent: VirtualNode?): VirtualNode
    fun registerWidget(type: String, builder: VirtualWidgetBuilder)
    fun <T> registerWidget(
        type: String,
        fromJsonT: (JsonLike) -> T,
        builder: (T, Map<String, List<VirtualNode>>?) -> VirtualNode
    )
    fun registerJsonWidget(
        type: String,
        builder: (JsonLike, Map<String, List<VirtualNode>>?) -> VirtualNode
    )
    fun dispose()
}

/** Default virtual widget registry implementation */
class DefaultVirtualWidgetRegistry(private val componentBuilder: ComponentBuilder) :
    VirtualWidgetRegistry {

    private val builders = mutableMapOf<String, VirtualWidgetBuilder>()

    companion object {
        // Default builders would be added here, but since implementations are not provided, leaving empty
        val defaultBuilders = mapOf<String, VirtualWidgetBuilder>()
    }

    init {
        builders.putAll(defaultBuilders)
    }

    override fun createWidget(data: VWData, parent: VirtualNode?): VirtualNode {
        return when (data) {
            is VWNodeData -> {
                val builder = builders[data.type]


//                    throw IllegalArgumentException("Unknown widget type: ${data.type}")
                if(builder!=null) return builder(data, parent, this)
                else     dummyBuilder(VWNodeData(
                    refName = data.refName,
                    type = data.type,
                    props = Props(value = mapOf(
                        "message" to "No builder registered for widget type '${data.type}'"
                    )),
                    commonProps = data.commonProps,
                    parentProps = data.parentProps,
                    childGroups = data.childGroups
                ), parent, this)


            }
            is VWComponentData -> {
                VirtualBuilderWidget(
                    refName = data.refName,
                    commonProps = data.commonProps,
                    parent = parent,
                    builder = { payload ->
                        val args = data.args?.mapValues { payload.evalExpr(it.value) }
                        componentBuilder(data.id, args)
                    }
                )
            }
            is VWStateData -> {
                // Create state container widget with resolved children
                val childGroups = createChildGroups(data.childGroups, parent, this)
                VWStateContainer(
                    refName = data.refName,
                    parent = parent,
                    parentProps = data.parentProps,
                    initStateDefs = data.initStateDefs,
                    childGroups = childGroups
                )
            }
        }
    }

    override fun registerWidget(type: String, builder: VirtualWidgetBuilder) {
        builders[type] = builder
    }

    /** Helper to register a widget with this registry */
    internal fun register(type: String, builder: VirtualWidgetBuilder) {
        builders[type] = builder
    }

    override fun <T> registerWidget(
        type: String,
        fromJsonT: (JsonLike) -> T,
        builder: (T, Map<String, List<VirtualNode>>?) -> VirtualNode
    ) {
        builders[type] = { data, parent, registry ->
            val childGroups = createChildGroups(data.childGroups, parent, registry)
            builder(fromJsonT(data.props.value), childGroups)
        }
    }

    override fun registerJsonWidget(
        type: String,
        builder: (JsonLike, Map<String, List<VirtualNode>>?) -> VirtualNode
    ) {
        builders[type] = { data, parent, registry ->
            val childGroups = createChildGroups(data.childGroups, parent, registry)
            builder(data.props.value, childGroups)
        }
    }

    override fun dispose() {
        builders.clear()
    }

    private fun createChildGroups(
        childGroups: Map<String, List<VWData>>?,
        parent: VirtualNode?,
        registry: VirtualWidgetRegistry
    ): Map<String, List<VirtualNode>>? {
        return childGroups?.mapValues { (_, children) ->
            children.map { registry.createWidget(it, parent) }
        }
    }
}

class VirtualBuilderWidget(
    refName: String?,
    commonProps: CommonProps?,
    parent: VirtualNode?,
    val builder: @Composable (RenderPayload) -> Unit
) : VirtualLeafNode<Props>(
    props = Props.empty(),
    commonProps = commonProps,
    parent = parent,
    refName = refName
) {

    @Composable
    override fun Render(payload: RenderPayload) {
        Box(modifier = Modifier.buildModifier(payload)){
            builder(payload)
        }
    }
}


fun registerAllChildern(childGroups: Map<String, List<VWData>>?, parent: VirtualNode?, registry: VirtualWidgetRegistry,): Map<String, List<VirtualNode>>? {
   return childGroups?.mapValues { (_, childrenData) ->
        childrenData.map { childData ->
            registry.createWidget(childData, parent)
        }
    }
}
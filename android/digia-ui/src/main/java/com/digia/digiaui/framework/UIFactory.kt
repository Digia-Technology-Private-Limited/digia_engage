package com.digia.digiaui.framework

import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.models.VWData
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.utils.JsonLike

/**
 * UIFactory - Convenience factory for creating UI elements from JSON
 * 
 * This class provides a simplified interface for creating widgets and UI structures
 * from JSON definitions, similar to the Dart ui_factory.dart implementation.
 * 
 * It wraps the VirtualWidgetRegistry to provide convenient static methods for
 * common UI creation patterns.
 * 
 * Example usage:
 * ```kotlin
 * // Parse JSON UI definition
 * val widgetData = UIFactory.parseWidget(jsonMap)
 * 
 * // Create widget from parsed data
 * val widget = UIFactory.createWidget(widgetData, registry)
 * 
 * // Create widget directly from JSON
 * val widget = UIFactory.createFromJson(jsonMap, registry)
 * ```
 */
object UIFactory {
    

    
    /**
     * Builder class for constructing widgets programmatically
     * 
     * Example:
     * ```kotlin
     * val widget = UIFactory.builder("digia/text")
     *     .prop("text", "Hello World")
     *     .prop("color", "#FF0000")
     *     .build(registry)
     * ```
     */
    class WidgetBuilder(private val type: String) {
        private val props = mutableMapOf<String, Any?>()
        private var refName: String? = null
        private val childGroups = mutableMapOf<String, MutableList<VWData>>()
        
        fun refName(name: String): WidgetBuilder {
            this.refName = name
            return this
        }
        
        fun prop(key: String, value: Any?): WidgetBuilder {
            props[key] = value
            return this
        }
        
        fun props(properties: Map<String, Any?>): WidgetBuilder {
            props.putAll(properties)
            return this
        }
        
        fun child(groupName: String = "children", child: VWData): WidgetBuilder {
            childGroups.getOrPut(groupName) { mutableListOf() }.add(child)
            return this
        }
        
        fun children(groupName: String = "children", children: List<VWData>): WidgetBuilder {
            childGroups.getOrPut(groupName) { mutableListOf() }.addAll(children)
            return this
        }
        
        fun toData(): VWNodeData {
            return VWNodeData(
                type = type,
                refName = refName,
                props = com.digia.digiaui.framework.models.Props(props),
                childGroups = if (childGroups.isEmpty()) null else childGroups
            )
        }
        
        fun build(registry: VirtualWidgetRegistry, parent: VirtualNode? = null): VirtualNode {
            return registry.createWidget(toData(), parent)
        }
    }
    
    /**
     * Create a widget builder for programmatic widget construction
     * 
     * @param type Widget type (e.g., "digia/text")
     * @return WidgetBuilder instance
     */
    fun builder(type: String): WidgetBuilder {
        return WidgetBuilder(type)
    }
}

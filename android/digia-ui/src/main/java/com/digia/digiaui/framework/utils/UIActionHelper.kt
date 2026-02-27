//package com.digia.digiaui.framework.utils
//
//import com.digia.digiaui.framework.UIFactory
//import com.digia.digiaui.framework.VirtualWidgetRegistry
//import com.digia.digiaui.framework.actions.ActionFactory
//import com.digia.digiaui.framework.actions.base.Action
//import com.digia.digiaui.framework.actions.base.ActionFlow
//import com.digia.digiaui.framework.base.VirtualNode
//import com.digia.digiaui.framework.models.VWData
//
///**
// * UIActionHelper - Integration utilities for using UIFactory with Actions
// *
// * Provides convenient methods for creating widgets with actions attached,
// * parsing complex UI structures, and handling action flows.
// */
//object UIActionHelper {
//
//    /**
//     * Create a widget with onClick action
//     *
//     * Example:
//     * ```kotlin
//     * val button = UIActionHelper.createWidgetWithAction(
//     *     widgetType = "digia/text",
//     *     props = mapOf("text" to "Click me"),
//     *     actionJson = mapOf(
//     *         "type" to "Action.showToast",
//     *         "data" to mapOf("message" to "Clicked!")
//     *     ),
//     *     registry = registry
//     * )
//     * ```
//     */
//    fun createWidgetWithAction(
//        widgetType: String,
//        props: Map<String, Any?>,
//        actionJson: JsonLike,
//        registry: VirtualWidgetRegistry
//    ): VirtualNode {
//        // Create action
//        val action = ActionFactory.fromJson(actionJson)
//
//        // Build widget with action in containerProps
//        val widgetJson: JsonLike = mutableMapOf(
//            "type" to widgetType,
//            "props" to props,
//            "containerProps" to mapOf(
//                "onClick" to mapOf(
//                    "actions" to listOf(actionJson)
//                )
//            )
//        )
//
//        return UIFactory.createFromJson(widgetJson, registry)
//    }
//
//    /**
//     * Create a clickable widget that updates state
//     *
//     * Example:
//     * ```kotlin
//     * val counter = UIActionHelper.createClickableStateWidget(
//     *     widgetType = "digia/text",
//     *     displayText = "Counter: @{count}",
//     *     stateUpdates = mapOf("count" to "@{count + 1}"),
//     *     registry = registry
//     * )
//     * ```
//     */
//    fun createClickableStateWidget(
//        widgetType: String,
//        displayText: String,
//        stateUpdates: Map<String, Any?>,
//        registry: VirtualWidgetRegistry
//    ): VirtualNode {
//        val setStateAction: JsonLike = mutableMapOf(
//            "type" to "Action.setState",
//            "data" to mapOf(
//                "state" to stateUpdates
//            )
//        )
//
//        return createWidgetWithAction(
//            widgetType = widgetType,
//            props = mapOf("text" to displayText),
//            actionJson = setStateAction,
//            registry = registry
//        )
//    }
//
//    /**
//     * Parse a complete UI structure with embedded actions
//     *
//     * This handles JSON structures that contain both widget definitions
//     * and action flows embedded in the widget properties.
//     */
//    fun parseUIWithActions(json: JsonLike): Pair<VWData, List<Action>?> {
//        // Parse widget data
//        val widgetData = UIFactory.parseWidget(json)
//
//        // Extract actions if present in containerProps
//        val actions = (json["containerProps"] as? Map<*, *>)?.let { containerProps ->
//            (containerProps["onClick"] as? Map<*, *>)?.let { onClick ->
//                (onClick["actions"] as? List<*>)?.mapNotNull { actionJson ->
//                    (actionJson as? JsonLike)?.let { ActionFactory.fromJson(it) }
//                }
//            }
//        }
//
//        return widgetData to actions
//    }
//
//    /**
//     * Create a form field with validation action
//     *
//     * Example:
//     * ```kotlin
//     * val emailField = UIActionHelper.createFormFieldWithValidation(
//     *     fieldType = "digia/textField",
//     *     stateKey = "email",
//     *     validationExpression = "@{isValidEmail(email)}",
//     *     errorMessage = "Invalid email address",
//     *     registry = registry
//     * )
//     * ```
//     */
//    fun createFormFieldWithValidation(
//        fieldType: String,
//        stateKey: String,
//        validationExpression: String,
//        errorMessage: String,
//        registry: VirtualWidgetRegistry
//    ): VirtualNode {
//        val json: JsonLike = mutableMapOf(
//            "type" to fieldType,
//            "refName" to stateKey,
//            "props" to mapOf(
//                "value" to "@{$stateKey}",
//                "errorText" to "@{!$validationExpression ? '$errorMessage' : null}"
//            ),
//            "containerProps" to mapOf(
//                "onChange" to mapOf(
//                    "actions" to listOf(
//                        mapOf(
//                            "type" to "Action.setState",
//                            "data" to mapOf(
//                                "state" to mapOf(
//                                    stateKey to "@{value}"
//                                )
//                            )
//                        )
//                    )
//                )
//            )
//        )
//
//        return UIFactory.createFromJson(json, registry)
//    }
//
//    /**
//     * Create a list of widgets with mapped actions
//     *
//     * Useful for creating dynamic lists where each item has its own action.
//     */
//    fun createWidgetListWithActions(
//        items: List<Map<String, Any?>>,
//        widgetType: String,
//        createActionForItem: (Map<String, Any?>, Int) -> JsonLike,
//        registry: VirtualWidgetRegistry
//    ): List<VirtualNode> {
//        return items.mapIndexed { index, item ->
//            val actionJson = createActionForItem(item, index)
//            createWidgetWithAction(
//                widgetType = widgetType,
//                props = item,
//                actionJson = actionJson,
//                registry = registry
//            )
//        }
//    }
//
//    /**
//     * Create a multi-action button
//     *
//     * Executes multiple actions in sequence when clicked.
//     */
//    fun createMultiActionButton(
//        text: String,
//        actions: List<JsonLike>,
//        registry: VirtualWidgetRegistry
//    ): VirtualNode {
//        val json: JsonLike = mutableMapOf(
//            "type" to "digia/text",  // or "digia/button" if available
//            "props" to mapOf("text" to text),
//            "containerProps" to mapOf(
//                "onClick" to mapOf(
//                    "actions" to actions
//                )
//            )
//        )
//
//        return UIFactory.createFromJson(json, registry)
//    }
//
//    /**
//     * Create a toggle widget (checkbox/switch) with state action
//     */
//    fun createToggleWidget(
//        widgetType: String,
//        stateKey: String,
//        label: String,
//        registry: VirtualWidgetRegistry
//    ): VirtualNode {
//        val json: JsonLike = mutableMapOf(
//            "type" to "digia/row",
//            "childGroups" to mapOf(
//                "children" to listOf(
//                    mapOf(
//                        "type" to widgetType,
//                        "props" to mapOf(
//                            "value" to "@{$stateKey}"
//                        ),
//                        "containerProps" to mapOf(
//                            "onClick" to mapOf(
//                                "actions" to listOf(
//                                    mapOf(
//                                        "type" to "Action.setState",
//                                        "data" to mapOf(
//                                            "state" to mapOf(
//                                                stateKey to "@{!$stateKey}"
//                                            )
//                                        )
//                                    )
//                                )
//                            )
//                        )
//                    ),
//                    mapOf(
//                        "type" to "digia/text",
//                        "props" to mapOf("text" to label)
//                    )
//                )
//            )
//        )
//
//        return UIFactory.createFromJson(json, registry)
//    }
//
//    /**
//     * Parse action flow and create associated UI elements
//     *
//     * Useful for creating UI controls that trigger specific action flows.
//     */
//    fun createActionTriggers(
//        actionFlowJson: JsonLike,
//        triggerWidgets: List<Map<String, Any?>>,
//        registry: VirtualWidgetRegistry
//    ): List<VirtualNode> {
//        return triggerWidgets.map { widgetConfig ->
//            val json: JsonLike = mutableMapOf(
//                "type" to (widgetConfig["type"] as? String ?: "digia/text"),
//                "props" to widgetConfig.filterKeys { it != "type" },
//                "containerProps" to mapOf(
//                    "onClick" to actionFlowJson
//                )
//            )
//
//            UIFactory.createFromJson(json, registry)
//        }
//    }
//
//    /**
//     * Create a conditional widget based on state with action
//     *
//     * Example: Show different content based on state and provide actions to change state
//     */
//    fun createConditionalWidget(
//        condition: String,
//        trueWidget: JsonLike,
//        falseWidget: JsonLike,
//        registry: VirtualWidgetRegistry
//    ): VirtualNode {
//        // This would need conditional rendering support in the framework
//        // For now, return a placeholder that demonstrates the pattern
//        val json: JsonLike = mutableMapOf(
//            "type" to "digia/column",
//            "props" to mapOf(
//                "visible" to condition
//            ),
//            "childGroups" to mapOf(
//                "children" to listOf(trueWidget)
//            )
//        )
//
//        return UIFactory.createFromJson(json, registry)
//    }
//}
//
///**
// * Extension functions for easy action integration
// */
//
///**
// * Add an onClick action to a widget JSON
// */
//fun JsonLike.withOnClick(actionJson: JsonLike): JsonLike {
//    val containerProps = (this["containerProps"] as? MutableMap<String, Any?>) ?: mutableMapOf()
//    containerProps["onClick"] = mapOf("actions" to listOf(actionJson))
//    this["containerProps"] = containerProps
//    return this
//}
//
///**
// * Add setState action to a widget JSON
// */
//fun JsonLike.withSetState(stateUpdates: Map<String, Any?>): JsonLike {
//    val actionJson: JsonLike = mutableMapOf(
//        "type" to "Action.setState",
//        "data" to mapOf("state" to stateUpdates)
//    )
//    return this.withOnClick(actionJson)
//}
//
///**
// * Add showToast action to a widget JSON
// */
//fun JsonLike.withToast(message: String, duration: String = "short"): JsonLike {
//    val actionJson: JsonLike = mutableMapOf(
//        "type" to "Action.showToast",
//        "data" to mapOf(
//            "message" to message,
//            "duration" to duration
//        )
//    )
//    return this.withOnClick(actionJson)
//}

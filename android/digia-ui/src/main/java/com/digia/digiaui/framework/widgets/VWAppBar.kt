package com.digia.digiaui.framework.widgets

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import LocalUIResources
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.actions.LocalActionExecutor
import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.base.VirtualCompositeNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.evalColor
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.registerAllChildern
import com.digia.digiaui.framework.state.LocalStateContextProvider
import com.digia.digiaui.framework.utils.JsonLike
import kotlinx.coroutines.launch

/**
 * AppBar widget properties
 */
data class AppBarProps(
    val title: JsonLike? = null,
    val elevation: ExprOr<Double>? = null,
    val shadowColor: ExprOr<String>? = null,
    val backgroundColor: ExprOr<String>? = null,
    val iconColor: ExprOr<String>? = null,
    val leadingIcon: JsonLike? = null,
    val automaticallyImplyLeading: ExprOr<Boolean>? = null,
    val defaultButtonColor: ExprOr<String>? = null,
    val onTapLeadingIcon: ActionFlow? = null,
    val trailingIcon: JsonLike? = null,
    val centerTitle: ExprOr<Boolean>? = null,
    val visibility: ExprOr<Boolean>? = null
) {
    companion object {
        fun fromJson(json: JsonLike): AppBarProps {
            return AppBarProps(
                title = json["title"] as? JsonLike,
                elevation = ExprOr.fromValue(json["elevation"]),
                shadowColor = ExprOr.fromValue(json["shadowColor"]),
                backgroundColor = ExprOr.fromValue(json["backgroundColor"] ?: json["backgrounColor"]),
                iconColor = ExprOr.fromValue(json["iconColor"]),
                leadingIcon = json["leadingIcon"] as? JsonLike,
                automaticallyImplyLeading = ExprOr.fromValue(json["automaticallyImplyLeading"]),
                defaultButtonColor = ExprOr.fromValue(json["defaultButtonColor"]),
                onTapLeadingIcon = (json["onTapLeadingIcon"] as? JsonLike)?.let { ActionFlow.fromJson(it) },
                trailingIcon = json["trailingIcon"] as? JsonLike,
                centerTitle = ExprOr.fromValue(json["centerTitle"]),
                visibility = ExprOr.fromValue(json["visibility"])
            )
        }
    }
}

/**
 * Virtual AppBar Widget
 * 
 * Implements Material3 TopAppBar with support for:
 * - Title (text or custom widget)
 * - Leading icon/widget
 * - Action buttons
 * - Background and icon color customization
 * - Visibility control
 */
class VWAppBar(
    refName: String? = null,
    commonProps: CommonProps? = null,
    props: AppBarProps,
    parent: VirtualNode? = null,
    slots: ((VirtualCompositeNode<AppBarProps>) -> Map<String, List<VirtualNode>>?)? = null,
    parentProps: Props? = null
) : VirtualCompositeNode<AppBarProps>(
    props = props,
    commonProps = commonProps,
    parentProps = parentProps,
    parent = parent,
    refName = refName,
    _slots = slots
) {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Render(payload: RenderPayload) {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val actionExecutor = LocalActionExecutor.current
        val stateContext = LocalStateContextProvider.current
        val resources = LocalUIResources.current

        // Get children
        val titleWidget = slot("title")
        val leadingWidget = slot("leading")
        val actionsWidgets = slotChildren("actions")

        // Evaluate properties
        val backgroundColor = payload.evalExpr(props.backgroundColor)
            ?.let { payload.evalColor(it) }
        val iconColor = payload.evalExpr(props.iconColor)
            ?.let { payload.evalColor(it) }
        val visibility = payload.evalExpr(props.visibility) ?: true

        // Check visibility
        if (!visibility) {
            return
        }

        // Build title content
        val titleContent = _buildTitle(payload, titleWidget)

        // Build leading content
        val leadingContent: (@Composable () -> Unit)? = _buildLeading(payload, leadingWidget, scope, context, actionExecutor, stateContext, resources)

        // Build actions content
        val actionsContent: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
            _buildActions(payload, actionsWidgets)
        }

        // Render Material3 TopAppBar
        TopAppBar(
            title = titleContent,
            navigationIcon = leadingContent ?: {},
            actions = actionsContent,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = backgroundColor ?: TopAppBarDefaults.topAppBarColors().containerColor,
                scrolledContainerColor = backgroundColor ?: TopAppBarDefaults.topAppBarColors().scrolledContainerColor,
                navigationIconContentColor = iconColor ?: TopAppBarDefaults.topAppBarColors().navigationIconContentColor,
                actionIconContentColor = iconColor ?: TopAppBarDefaults.topAppBarColors().actionIconContentColor,
                titleContentColor = iconColor ?: TopAppBarDefaults.topAppBarColors().titleContentColor
            )
        )
    }

    /**
     * Build the title widget or text
     */
    private fun _buildTitle(payload: RenderPayload, titleWidget: VirtualNode?): @Composable () -> Unit {
        return {
            if (titleWidget != null) {
                titleWidget.ToWidget(payload)
            } else if (props.title != null) {
                val titleText = (props.title["text"] as? String) ?: ""
                Text(
                    text = titleText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text("")
            }
        }
    }

    /**
     * Build the leading icon/widget
     */
    private fun _buildLeading(
        payload: RenderPayload,
        leadingWidget: VirtualNode?,
        scope: kotlinx.coroutines.CoroutineScope,
        context: android.content.Context,
        actionExecutor: com.digia.digiaui.framework.actions.ActionExecutor,
        stateContext: com.digia.digiaui.framework.state.StateContext?,
        resources: com.digia.digiaui.framework.UIResources
    ): (@Composable () -> Unit)? {
        if (leadingWidget != null) {
            return { leadingWidget.ToWidget(payload) }
        }

        val leadingIconProps = props.leadingIcon?.let { VWIconProps.fromJson(it) }
        if (leadingIconProps == null) return null

        val iconWidget = VWIcon(
            props = leadingIconProps,
            commonProps = null,
            parent = this
        )

        return {
            IconButton(
                onClick = {
                    props.onTapLeadingIcon?.let { actionFlow ->
                        scope.launch {
                            payload.executeAction(
                                context = context,
                                actionFlow = actionFlow,
                                actionExecutor = actionExecutor,
                                stateContext = stateContext,
                                resourcesProvider = resources,
                                incomingScopeContext = null
                            )
                        }
                    }
                }
            ) {
                iconWidget.ToWidget(payload)
            }
        }
    }

    /**
     * Build the actions (trailing icons/widgets)
     */
    @Composable
    private fun androidx.compose.foundation.layout.RowScope._buildActions(
        payload: RenderPayload,
        actionsWidgets: List<VirtualNode>
    ) {
        if (actionsWidgets.isNotEmpty()) {
            actionsWidgets.forEach { action ->
                action.ToWidget(payload)
            }
        } else {
            val trailingIconProps = props.trailingIcon?.let { VWIconProps.fromJson(it) }
            if (trailingIconProps != null) {
                val iconWidget = VWIcon(
                    props = trailingIconProps,
                    commonProps = null,
                    parent = this@VWAppBar
                )
                iconWidget.ToWidget(payload)
            }
        }
    }
}




/**
 * Builder function for AppBar widget
 */
fun appBarBuilder(
    data: VWNodeData,
    parent: VirtualNode?,
    registry: VirtualWidgetRegistry
): VirtualNode {
    return VWAppBar(
        refName = data.refName,
        commonProps = data.commonProps,
        parent = parent,
        parentProps = data.parentProps,
        props = AppBarProps.fromJson(data.props.value),
        slots = {
                self ->
            registerAllChildern(data.childGroups, self, registry)
        },
    )
}
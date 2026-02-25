package com.digia.digiaui.framework.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.base.VirtualCompositeNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.evalColor
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.registerAllChildern
import com.digia.digiaui.framework.utils.JsonLike

/**
 * Scaffold widget properties
 */
data class ScaffoldProps(
    val scaffoldBackgroundColor: ExprOr<String>? = null,
    val enableSafeArea: ExprOr<Boolean>? = null,
    val resizeToAvoidBottomInset: ExprOr<Boolean>? = null
) {
    companion object {
        fun fromJson(json: JsonLike): ScaffoldProps {
            return ScaffoldProps(
                scaffoldBackgroundColor = ExprOr.fromValue(json["scaffoldBackgroundColor"]),
                enableSafeArea = ExprOr.fromValue(json["enableSafeArea"]),
                resizeToAvoidBottomInset = ExprOr.fromValue(json["resizeToAvoidBottomInset"])
            )
        }
    }
}

/**
 * Virtual Scaffold Widget
 * 
 * Implements Material Design scaffold with support for:
 * - App bar
 * - Body content
 * - Drawer (left)
 * - End drawer (right)
 * - Bottom navigation bar
 * - Persistent footer buttons
 * - Safe area
 */
class VWScaffold(
    refName: String? = null,
    commonProps: CommonProps? = null,
    props: ScaffoldProps,
    parent: VirtualNode? = null,
    slots: ((VirtualCompositeNode<ScaffoldProps>) -> Map<String, List<VirtualNode>>?)? = null,
    parentProps: Props? = null
) : VirtualCompositeNode<ScaffoldProps>(
    props = props,
    commonProps = commonProps,
    parentProps = parentProps,
    parent = parent,
    refName = refName,
    _slots = slots
) {

    @Composable
    override fun Render(payload: RenderPayload) {

        /* ----------------------------------------
         * Slots
         * ---------------------------------------- */
        val appBar = slot("appBar")
        val body = slot("body")
        val drawer = slot("drawer")
        val endDrawer = slot("endDrawer")
        val bottomNavigationBar = slot("bottomNavigationBar")
        val persistentFooterButtons = slotChildren("persistentFooterButtons")

        /* ----------------------------------------
         * Props
         * ---------------------------------------- */
        val scaffoldBackgroundColor =
            payload.evalExpr(props.scaffoldBackgroundColor)
                ?.let { payload.evalColor(it) }

        val enableSafeArea =
            payload.evalExpr(props.enableSafeArea) ?: true

        val resizeToAvoidBottomInset =
            payload.evalExpr(props.resizeToAvoidBottomInset) ?: true

        /* ----------------------------------------
         * Drawer state
         * ---------------------------------------- */
        val drawerState = rememberDrawerState(DrawerValue.Closed)

        /* ----------------------------------------
         * Insets handling
         * ---------------------------------------- */
        val contentWindowInsets =
            if (enableSafeArea) {
                WindowInsets.systemBars
            } else {
                WindowInsets(0)
            }

        val imePaddingModifier =
            if (resizeToAvoidBottomInset) {
                Modifier.imePadding()
            } else {
                Modifier
            }

        /* ----------------------------------------
         * Scaffold content
         * ---------------------------------------- */
        val scaffoldContent: @Composable () -> Unit = {
            Scaffold(
                topBar = {
                    appBar?.ToWidget(payload)
                },
                bottomBar = {
                    bottomNavigationBar?.ToWidget(payload)
                },
                containerColor = scaffoldBackgroundColor ?: Color.Unspecified,
                contentWindowInsets = contentWindowInsets
            ) { paddingValues ->

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .then(imePaddingModifier)
                ) {

                    /* -------- Body -------- */
                    body?.ToWidget(payload)

                    /* -------- Persistent Footer -------- */
                    if (persistentFooterButtons.isNotEmpty()) {
                        Row(
                            modifier = Modifier.align(Alignment.BottomCenter)
                                .padding(16.dp)
                        ) {
                            persistentFooterButtons.forEach { button ->
                                button.ToWidget(payload)
                            }
                        }
                    }
                }
            }
        }

        /* ----------------------------------------
         * Drawer wrapping
         * ---------------------------------------- */
        when {
            drawer != null && endDrawer != null -> {
                val endDrawerState =
                    rememberDrawerState(DrawerValue.Closed)

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            drawer.ToWidget(payload)
                        }
                    }
                ) {
                    ModalNavigationDrawer(
                        drawerState = endDrawerState,
                        drawerContent = {
                            ModalDrawerSheet {
                                endDrawer.ToWidget(payload)
                            }
                        },
                        content = scaffoldContent
                    )
                }
            }

            drawer != null -> {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            drawer.ToWidget(payload)
                        }
                    },
                    content = scaffoldContent
                )
            }

            endDrawer != null -> {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            endDrawer.ToWidget(payload)
                        }
                    },
                    content = scaffoldContent
                )
            }

            else -> {
                scaffoldContent()
            }
        }
    }
}


/**
 * Builder function for Scaffold widget
 */
fun scaffoldBuilder(
    data: VWNodeData,
    parent: VirtualNode?,
    registry: VirtualWidgetRegistry
): VirtualNode {

    return VWScaffold(
        refName = data.refName,
        commonProps = data.commonProps,
        parent = parent,
        parentProps = data.parentProps,
        props = ScaffoldProps.fromJson(data.props.value),
        slots = {
                self ->
            registerAllChildern(data.childGroups, self, registry)
        },
    )
}

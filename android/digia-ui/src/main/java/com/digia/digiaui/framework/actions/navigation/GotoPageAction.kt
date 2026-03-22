package com.digia.digiaui.framework.actions.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.framework.actions.base.Action
import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.actions.base.ActionId
import com.digia.digiaui.framework.actions.base.ActionProcessor
import com.digia.digiaui.framework.actions.base.ActionType
import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.navigation.DigiaUINavigationActivity
import com.digia.digiaui.framework.navigation.NavigationManager
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.framework.utils.asSafe
import com.digia.digiaui.utils.asSafe

/**
 * GotoPage Action (NavigateToPageAction)
 *
 * Navigates to a specific page in the application. Supports various navigation modes including
 * waiting for results and stack manipulation.
 */
data class GotoPageAction(
        override var actionId: ActionId? = null,
        override var disableActionIf: ExprOr<Boolean>? = null,
        val pageData: ExprOr<JsonLike>? = null,
        val waitForResult: ExprOr<Boolean>? = null,
        val shouldRemovePreviousScreensInStack: ExprOr<Boolean>? = null,
        val routeNameToRemoveUntil: ExprOr<String>? = null,
        val onResult: ActionFlow? = null
) : Action {
    override val actionType = ActionType.NAVIGATE_TO_PAGE

    override fun toJson(): JsonLike {
        return mapOf(
                "type" to actionType.value,
                "pageData" to pageData?.toJson(),
                "waitForResult" to waitForResult?.toJson(),
                "shouldRemovePreviousScreensInStack" to
                        shouldRemovePreviousScreensInStack?.toJson(),
                "routeNameToRemoveUntil" to routeNameToRemoveUntil?.toJson(),
                "onResult" to onResult?.toJson()
        )
    }

    companion object {
        fun fromJson(json: JsonLike): GotoPageAction {
            return GotoPageAction(
                    pageData = json["pageData"]?.let { ExprOr.fromValue<JsonLike>(it) },
                    waitForResult = json["waitForResult"]?.let { ExprOr.fromValue<Boolean>(it) },
                    shouldRemovePreviousScreensInStack =
                            json["shouldRemovePreviousScreensInStack"]?.let {
                                ExprOr.fromValue<Boolean>(it)
                            },
                    routeNameToRemoveUntil =
                            json["routeNameToRemoveUntil"]?.let { ExprOr.fromValue<String>(it) },
                    onResult = json["onResult"]?.let { ActionFlow.fromJson(it as? JsonLike) }
            )
        }
    }
}

/** GotoPage Action Processor */
class GotoPageProcessor : ActionProcessor<GotoPageAction>() {
    override suspend fun execute(
            context: Context,
            action: GotoPageAction,
            scopeContext: ScopeContext?,
            stateContext: com.digia.digiaui.framework.state.StateContext?,
            resourcesProvider: UIResources?,
            id: String
    ): Any? {
        try {
            // Evaluate page data - matches Flutter's deepEvaluate
            val pageEvaluatedData = action.pageData?.deepEvaluate(scopeContext)
            val pageData = asSafe<JsonLike>(pageEvaluatedData)
            if (pageData == null) {
                println("GotoPageAction: No pageData provided")
                return null
            }

            // Extract page ID from pageData
            val pageId = asSafe<String>(pageData["id"])

            if (pageId == null) {
                throw IllegalArgumentException("Null value for 'id' in pageData")
            }

            // Extract page arguments - Flutter uses 'args' field
            val evaluatedArgs = asSafe<JsonLike>(pageData["args"])

            // Evaluate navigation flags
            val shouldRemovePreviousScreens =
                    action.shouldRemovePreviousScreensInStack?.evaluate<Boolean>(scopeContext)
                            ?: false
            val routeNameToRemoveUntil =
                    action.routeNameToRemoveUntil?.evaluate<String>(scopeContext)
            val waitForResult = action.waitForResult?.evaluate<Boolean>(scopeContext) ?: false

            println("NavigateToPageProcessor: Navigate to $pageId with args: $evaluatedArgs")
            println("  - waitForResult: $waitForResult")
            println("  - shouldRemovePreviousScreens: $shouldRemovePreviousScreens")
            println("  - routeNameToRemoveUntil: $routeNameToRemoveUntil")

            // If no NavHost is active, fall back to launching DigiaUINavigationActivity
            if (!NavigationManager.isNavHostActive()) {
                println(
                        "NavigateToPageProcessor: No active NavHost, launching DigiaUINavigationActivity for $pageId"
                )
                val intent =
                        DigiaUINavigationActivity.createIntent(
                                context = context,
                                startPageId = pageId,
                                pageArgs = evaluatedArgs
                        )
                if (context !is Activity) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                context.startActivity(intent)
                return null
            }

            // Handle different navigation modes based on Flutter logic
            when {
                // Pop until specific route with replace
                routeNameToRemoveUntil != null && shouldRemovePreviousScreens -> {
                    // Remove routes until the specified route, then navigate
                    NavigationManager.popTo(routeNameToRemoveUntil, inclusive = false)
                    NavigationManager.navigate(pageId, evaluatedArgs, replace = false)
                }
                // Just pop until specific route
                routeNameToRemoveUntil != null -> {
                    NavigationManager.popTo(routeNameToRemoveUntil, inclusive = false)
                }
                // Replace current screen (remove previous screens in stack)
                shouldRemovePreviousScreens -> {
                    NavigationManager.navigate(pageId, evaluatedArgs, replace = true)
                }
                // Regular push navigation
                else -> {
                    NavigationManager.navigate(pageId, evaluatedArgs, replace = false)
                }
            }

            // Handle result callback if waitForResult is true
            if (waitForResult && action.onResult != null) {

                NavigationManager.registerResultCallback(
                        pageId = pageId,
                        onResult = action.onResult,
                        scopeContext = scopeContext,
                        stateContext = stateContext
                )
                println("NavigateToPageProcessor: Registered result callback for $pageId")
            }

            return null
        } catch (error: Exception) {
            println("NavigateToPageProcessor error: ${error.message}")
            error.printStackTrace()
            throw error
        }
    }
}

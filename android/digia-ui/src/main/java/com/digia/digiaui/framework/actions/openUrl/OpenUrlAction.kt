package com.digia.digiaui.framework.actions.openUrl

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.digia.digiaui.framework.actions.base.Action
import com.digia.digiaui.framework.actions.base.ActionId
import com.digia.digiaui.framework.actions.base.ActionProcessor
import com.digia.digiaui.framework.actions.base.ActionType
import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.utils.JsonLike
import androidx.core.net.toUri
import com.digia.digiaui.framework.UIResources


enum class LaunchMode {
    IN_APP_WEBVIEW,
    EXTERNAL_APPLICATION,
    EXTERNAL_NON_BROWSER_APPLICATION,
    PLATFORM_DEFAULT
}

fun uriLaunchMode(value: Any?): LaunchMode =
    when (value) {
        "inAppWebView", "inApp" ->
            LaunchMode.IN_APP_WEBVIEW

        "externalApplication", "external" ->
            LaunchMode.EXTERNAL_APPLICATION

        "externalNonBrowserApplication" ->
            LaunchMode.EXTERNAL_NON_BROWSER_APPLICATION

        else ->
            LaunchMode.PLATFORM_DEFAULT
    }

/**
 * OpenUrl Action
 * 
 * Opens a URL in an external browser or appropriate app.
 * 
 * @param url The URL to open (can be an expression)
 * @param launchMode How to launch the URL (default: EXTERNAL_APPLICATION)
 */
data class OpenUrlAction(
    override var actionId: ActionId? = null,
    override var disableActionIf: ExprOr<Boolean>? = null,
    val url: ExprOr<String>?,
    val launchMode: String? = null
) : Action {
    override val actionType = ActionType.OPEN_URL

    override fun toJson(): JsonLike =
        mapOf(
            "type" to actionType.value,
            "url" to url?.toJson(),
            "launchMode" to launchMode
        )

    companion object {
        fun fromJson(json: JsonLike): OpenUrlAction {
            return OpenUrlAction(
                url = ExprOr.fromValue(json["url"]),
                launchMode = json["launchMode"] as String?
            )
        }
    }
}

/** Processor for open URL action */
class OpenUrlProcessor : ActionProcessor<OpenUrlAction>() {
    override suspend fun execute(
        context: Context,
        action: OpenUrlAction,
        scopeContext: ScopeContext?,
        stateContext: com.digia.digiaui.framework.state.StateContext?,
        resourcesProvider: UIResources?,
        id: String
    ) {
        // Evaluate URL
        val url = action.url?.evaluate(scopeContext) ?: ""
        if (url.isEmpty()) {
            println("OpenUrlAction: URL is empty")
            return
        }

        // Evaluate launch mode
        val launchModeStr = action.launchMode ?: "externalApplication"
        val launchMode = uriLaunchMode(launchModeStr)

        try {
            when (launchMode) {
                LaunchMode.IN_APP_WEBVIEW -> {
                    // TODO: Open in custom WebView activity
                    println("OpenUrlAction: IN_APP_WEBVIEW not yet implemented, using external browser")
                    openExternal(context, url)
                }
                LaunchMode.EXTERNAL_APPLICATION -> {
                    openExternal(context, url)
                }
                LaunchMode.EXTERNAL_NON_BROWSER_APPLICATION -> {
                    openExternalNonBrowser(context, url)
                }
                LaunchMode.PLATFORM_DEFAULT -> {
                    openPlatformDefault(context, url)
                }
            }
            println("OpenUrlAction: Opened URL: $url with mode: $launchMode")
        } catch (e: Exception) {
            println("OpenUrlAction: Failed to open URL: $url - ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun openExternal(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
    
    private fun openExternalNonBrowser(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Exclude browser apps
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        context.startActivity(intent)
    }
    
    private fun openPlatformDefault(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

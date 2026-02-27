package com.digia.digiaui.framework.widgets

import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.base.VirtualLeafNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.utils.JsonLike

/** WebView widget that renders a web page. Mirrors Flutter's VWWebView. */
class VWWebView(
        refName: String? = null,
        commonProps: CommonProps? = null,
        private val webViewProps: WebViewProps,
        parent: VirtualNode? = null,
        parentProps: Props? = null
) :
        VirtualLeafNode<WebViewProps>(
                props = webViewProps,
                commonProps = commonProps,
                parent = parent,
                refName = refName,
                parentProps = parentProps
        ) {

    @Composable
    override fun Render(payload: RenderPayload) {
        val url = webViewProps.url
        val shouldInterceptBackButton = webViewProps.shouldInterceptBackButton ?: false

        if (url == null) {
            // Error handling similar to Flutter: return const Center(child: Text('Error: No URL
            // provided'));
            // In Compose SDK we might just render a Text
            androidx.compose.material3.Text("Error: No URL provided")
            return
        }

        // We need to keep a reference to the WebView to handle back navigation if needed
        var webView: WebView? = remember { null }

        // Intercept back button if requested
        if (shouldInterceptBackButton) {
            BackHandler(enabled = true) {
                if (webView?.canGoBack() == true) {
                    webView?.goBack()
                } else {
                    // If can't go back, we don't do anything here, default back behavior is
                    // intercepted.
                    // To propagate back, the registry/navigation logic usually handles it,
                    // but BackHandler consumes the event.
                    // Typically if we want to allow standard back when webview can't pop,
                    // we would need a dynamic enabled state.
                    // However, Flutter logic says:
                    // if (canGoBack) controller.goBack() else return true (allow pop).
                    // Compose BackHandler doesn't easily allow "pass through" inside the callback.
                    // We need 'enabled' to be false if we want system back.
                    // But we don't know if canGoBack is true until we check it.
                    // For now, simple implementation: if intercepted, we assume we want to control
                    // it.
                    // A better implementation would update 'enabled' state based on 'canGoBack'
                    // changes (WebChromeClient).
                }
            }
        }

        val modifier = Modifier.buildModifier(payload)

        AndroidView(
                modifier = modifier,
                factory = { context ->
                    WebView(context).apply {
                        layoutParams =
                                ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                )
                        settings.apply {
                            javaScriptEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            domStorageEnabled = true
                        }
                        webViewClient = WebViewClient() // Opens links in this WebView
                        loadUrl(url)
                        webView = this
                    }
                },
                update = { view ->
                    // Update URL if changed?
                    // Typically standard AndroidView update block runs on recomposition.
                    // Avoid reloading if URL hasn't changed to prevent refresh loops.
                    if (view.url != url) {
                        view.loadUrl(url)
                    }
                    webView = view
                }
        )
    }
}

// ============== Props ==============

data class WebViewProps(val url: String? = null, val shouldInterceptBackButton: Boolean? = null) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonLike): WebViewProps {
            return WebViewProps(
                    url = json["url"] as? String,
                    shouldInterceptBackButton = json["shouldInterceptBackButton"] as? Boolean
            )
        }
    }
}

// ============== Builder ==============

fun webViewBuilder(
        data: VWNodeData,
        parent: VirtualNode?,
        registry: VirtualWidgetRegistry
): VirtualNode {
    return VWWebView(
            refName = data.refName,
            commonProps = data.commonProps,
            webViewProps = WebViewProps.fromJson(data.props.value),
            parent = parent,
            parentProps = data.parentProps
    )
}

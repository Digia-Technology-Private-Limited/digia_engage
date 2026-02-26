package com.digia.digiaui.framework.widgets

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.base.VirtualLeafNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.widgets.chart.ChartColorConverter
import com.digia.digiaui.framework.widgets.chart.ChartConfigBuilder
import com.digia.digiaui.framework.widgets.chart.ChartProps
import com.google.gson.Gson

/**
 * Virtual Widget for Chart rendering using WebView and Chart.js CDN.
 *
 * Supports two modes:
 * 1. Direct Data Source Mode: Uses complete Chart.js config from dataSource
 * 2. Individual Properties Mode: Builds config from chartType, labels, chartData, options
 *
 * Colors are automatically converted from Flutter AARRGGBB format to CSS RRGGBBAA.
 */
class VWChart(
        refName: String?,
        commonProps: CommonProps?,
        parent: VirtualNode?,
        parentProps: Props? = null,
        props: ChartProps
) :
        VirtualLeafNode<ChartProps>(
                props = props,
                commonProps = commonProps,
                parent = parent,
                refName = refName,
                parentProps = parentProps
        ) {

    @Composable
    override fun Render(payload: RenderPayload) {
        val modifier = Modifier.buildModifier(payload)

        if (props.useDataSource) {
            RenderFromDataSource(payload, modifier)
        } else {
            RenderFromProperties(payload, modifier)
        }
    }

    @Composable
    private fun RenderFromDataSource(payload: RenderPayload, modifier: Modifier) {
        val chartConfig = payload.evalExpr(props.dataSource)

        when {
            chartConfig == null -> {
                RenderError(
                        "Chart is configured to use a data source, but the `dataSource` property is not set.",
                        modifier
                )
            }
            chartConfig !is Map<*, *> -> {
                RenderError(
                        "The provided `dataSource` did not evaluate to a valid chart configuration map. Got type: ${chartConfig::class.simpleName}",
                        modifier
                )
            }
            else -> {
                @Suppress("UNCHECKED_CAST")
                val normalizedConfig =
                        ChartColorConverter.normalizeColorsInMap(chartConfig as Map<String, Any?>)
                RenderChart(normalizedConfig, modifier)
            }
        }
    }

    @Composable
    @Suppress("UNCHECKED_CAST")
    private fun RenderFromProperties(payload: RenderPayload, modifier: Modifier) {
        val chartType = payload.evalExpr(props.chartType) ?: "line"
        val labels = payload.evalExpr(props.labels)
        val chartData = payload.evalExpr(props.chartData)
        val options = props.options

        // Return placeholder if no chart data is provided
        if (chartData.isNullOrEmpty()) {
            RenderPlaceholder("No chart data provided.", modifier)
            return
        }

        // Validate chart type compatibility
        val validationError = ChartConfigBuilder.validateChartTypes(chartData)
        if (validationError != null) {
            RenderError(validationError, modifier)
            return
        }

        // Build Chart.js configuration
        val chartConfig =
                ChartConfigBuilder.buildChartConfig(
                        chartType = chartType,
                        labels = labels,
                        datasets = chartData,
                        options = options
                )

        RenderChart(chartConfig, modifier)
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun RenderChart(chartConfig: Map<String, Any?>, modifier: Modifier) {
        val gson = remember { Gson() }
        val chartConfigJson = remember(chartConfig) { gson.toJson(chartConfig) }

        val htmlContent = remember(chartConfigJson) { buildChartHtml(chartConfigJson) }

        AndroidView(
                modifier = modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams =
                                ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                )

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            builtInZoomControls = false
                            displayZoomControls = false
                            cacheMode = WebSettings.LOAD_NO_CACHE
                        }

                        webViewClient = WebViewClient()
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                update = { webView ->
                    webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                }
        )
    }

    /** Builds HTML template with Chart.js CDN for rendering the chart. */
    private fun buildChartHtml(chartConfigJson: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
                <style>
                    * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }
                    html, body {
                        width: 100%;
                        height: 100%;
                        overflow: hidden;
                        background: transparent;
                    }
                    .chart-container {
                        position: relative;
                        width: 100%;
                        height: 100%;
                    }
                    canvas {
                        width: 100% !important;
                        height: 100% !important;
                    }
                </style>
            </head>
            <body>
                <div class="chart-container">
                    <canvas id="chartCanvas"></canvas>
                </div>
                <script>
                    (function() {
                        try {
                            const ctx = document.getElementById('chartCanvas').getContext('2d');
                            const config = $chartConfigJson;
                            
                            // Ensure responsive behavior
                            if (!config.options) config.options = {};
                            config.options.responsive = true;
                            config.options.maintainAspectRatio = false;
                            
                            new Chart(ctx, config);
                        } catch (error) {
                            console.error('Chart.js Error:', error);
                            document.body.innerHTML = '<div style="color:red;padding:16px;">Error: ' + error.message + '</div>';
                        }
                    })();
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    @Composable
    private fun RenderPlaceholder(message: String, modifier: Modifier) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = message, color = Color.Gray, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }

    @Composable
    private fun RenderError(errorMessage: String, modifier: Modifier) {
        Box(modifier = modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "⚠️", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                        text = "Invalid Chart Configuration",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD32F2F),
                        textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                        text = errorMessage,
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Builder function for VWChart widget. */
fun chartBuilder(
        data: VWNodeData,
        parent: VirtualNode?,
        registry: VirtualWidgetRegistry
): VirtualNode {
    return VWChart(
            refName = data.refName,
            commonProps = data.commonProps,
            parent = parent,
            parentProps = data.parentProps,
            props = ChartProps.fromJson(data.props.value)
    )
}

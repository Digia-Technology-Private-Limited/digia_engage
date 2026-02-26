package com.digia.digiaui.framework.widgets.chart

import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.utils.JsonLike

/**
 * Chart widget properties matching the Flutter VWChart schema.
 *
 * Supports two modes:
 * 1. Direct Data Source Mode: When [useDataSource] is true, uses [dataSource] as complete Chart.js
 * config
 * 2. Individual Properties Mode: Uses [chartType], [labels], [chartData], and [options] to build
 * config
 */
data class ChartProps(
        /** If true, use dataSource as complete Chart.js config */
        val useDataSource: Boolean = false,

        /** Complete Chart.js configuration when useDataSource is true */
        val dataSource: ExprOr<Any>? = null,

        /** Chart type: 'line', 'bar', 'pie', 'doughnut', 'polarArea', 'radar' */
        val chartType: ExprOr<String>? = null,

        /** X-axis labels */
        val labels: ExprOr<List<Any>>? = null,

        /** Array of dataset configurations */
        val chartData: ExprOr<List<Any>>? = null,

        /** Chart.js options (responsive, legend, title, etc.) */
        val options: Map<String, Any?>? = null,

        /** Action triggered when chart data changes */
        val onChanged: ActionFlow? = null
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonLike): ChartProps {
            return ChartProps(
                    useDataSource = json["useDataSource"] as? Boolean ?: false,
                    dataSource = ExprOr.fromValue(json["dataSource"]),
                    chartType = ExprOr.fromValue(json["chartType"]),
                    labels = ExprOr.fromValue(json["labels"]),
                    chartData = ExprOr.fromValue(json["chartData"]),
                    options = json["options"] as? Map<String, Any?>,
                    onChanged =
                            (json["onChanged"] as? Map<String, Any?>)?.let {
                                ActionFlow.fromJson(it)
                            }
            )
        }
    }
}

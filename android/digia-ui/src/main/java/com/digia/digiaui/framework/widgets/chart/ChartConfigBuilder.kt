package com.digia.digiaui.framework.widgets.chart

/**
 * Builds Chart.js configuration from individual chart properties. Handles dataset cleaning, color
 * normalization, and options building.
 */
object ChartConfigBuilder {

    /**
     * Builds complete Chart.js config from flat structure.
     *
     * @param chartType The base chart type ('line', 'bar', 'pie', etc.)
     * @param labels X-axis labels
     * @param datasets List of dataset configurations
     * @param options Chart options (legend, title, responsive, etc.)
     * @return Complete Chart.js configuration map
     */
    @Suppress("UNCHECKED_CAST")
    fun buildChartConfig(
            chartType: String,
            labels: List<Any>?,
            datasets: List<Any>?,
            options: Map<String, Any?>?
    ): Map<String, Any?> {
        val datasetsList =
                datasets?.mapNotNull { ds ->
                    when (ds) {
                        is Map<*, *> -> cleanDataset(ds as Map<String, Any?>)
                        else -> null
                    }
                }
                        ?: emptyList()

        // Determine if this is a mixed chart
        val isMixed = chartType == "mixed" || hasMixedTypes(datasetsList)
        val effectiveType = if (isMixed) "bar" else chartType

        // Convert labels to List<String>
        val labelsList = labels?.map { it.toString() } ?: emptyList()

        return mapOf(
                "type" to effectiveType,
                "data" to mapOf("labels" to labelsList, "datasets" to datasetsList),
                "options" to buildOptions(options, datasetsList)
        )
    }

    /** Check if datasets have different types (mixed chart) */
    private fun hasMixedTypes(datasets: List<Map<String, Any?>>): Boolean {
        if (datasets.size <= 1) return false
        val firstType = datasets.first()["type"]
        return datasets.any { it["type"] != firstType }
    }

    /** Clean dataset by removing null/empty values and normalizing colors. */
    private fun cleanDataset(dataset: Map<String, Any?>): Map<String, Any?> {
        val cleaned =
                mutableMapOf<String, Any?>(
                        "label" to (dataset["label"] ?: ""),
                        "data" to
                                ((dataset["data"] as? List<*>)?.mapNotNull {
                                    when (it) {
                                        is Number -> it
                                        is String -> it.toDoubleOrNull()
                                        else -> null
                                    }
                                }
                                        ?: emptyList<Number>())
                )

        // Add type for mixed charts
        dataset["type"]?.takeIf { it.toString().isNotEmpty() }?.let { cleaned["type"] = it }

        // Add optional properties with color normalization
        addIfPresent(cleaned, dataset, "borderColor", normalizeColor = true)
        addIfPresent(cleaned, dataset, "backgroundColor", normalizeColor = true)
        addIfPresent(cleaned, dataset, "pointBackgroundColor", normalizeColor = true)
        addIfPresent(cleaned, dataset, "pointBorderColor", normalizeColor = true)
        addIfPresent(cleaned, dataset, "hoverBackgroundColor", normalizeColor = true)
        addIfPresent(cleaned, dataset, "hoverBorderColor", normalizeColor = true)

        addIfPresent(cleaned, dataset, "borderWidth")
        addIfPresent(cleaned, dataset, "tension")
        addIfPresent(cleaned, dataset, "fill")

        return cleaned
    }

    private fun addIfPresent(
            target: MutableMap<String, Any?>,
            source: Map<String, Any?>,
            key: String,
            normalizeColor: Boolean = false
    ) {
        val value = source[key]
        if (value != null && !(value is String && value.isEmpty())) {
            target[key] =
                    if (normalizeColor && value is String) {
                        ChartColorConverter.normalizeColor(value)
                    } else {
                        value
                    }
        }
    }

    /** Build Chart.js options object. */
    @Suppress("UNCHECKED_CAST")
    private fun buildOptions(
            optionsProp: Map<String, Any?>?,
            datasets: List<Map<String, Any?>>
    ): Map<String, Any?> {
        if (optionsProp == null) {
            return mapOf(
                    "responsive" to true,
                    "maintainAspectRatio" to false,
                    "plugins" to
                            mapOf(
                                    "legend" to mapOf("display" to true, "position" to "top"),
                                    "title" to mapOf("display" to false, "text" to "")
                            )
            )
        }

        return mapOf(
                "responsive" to (optionsProp["responsive"] ?: true),
                "maintainAspectRatio" to (optionsProp["maintainAspectRatio"] ?: false),
                "plugins" to
                        mapOf(
                                "legend" to buildLegendOptions(optionsProp["legend"], datasets),
                                "title" to buildTitleOptions(optionsProp["title"])
                        )
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildLegendOptions(
            legendProp: Any?,
            datasets: List<Map<String, Any?>>
    ): Map<String, Any?> {
        if (legendProp !is Map<*, *>) {
            return mapOf("display" to true, "position" to "top")
        }

        val legend = legendProp as Map<String, Any?>

        // Process label styles from first dataset
        val fontStyles =
                mutableMapOf<String, Any?>(
                        "family" to "Roboto",
                        "size" to 12,
                        "style" to "normal",
                        "weight" to "normal",
                        "lineHeight" to 1.2
                )
        var defaultColor = "#666"

        if (datasets.isNotEmpty()) {
            val firstDataset = datasets.first()
            val labelStyle = firstDataset["labelStyle"] as? Map<String, Any?>
            if (labelStyle != null) {
                fontStyles.putAll(buildFontOptions(labelStyle))
                labelStyle["textColor"]?.let { color ->
                    ChartColorConverter.normalizeColor(color)?.let { defaultColor = it }
                }
            }
        }

        return mapOf(
                "display" to (legend["display"] ?: true),
                "position" to (legend["position"] ?: "top"),
                "labels" to mapOf("font" to fontStyles, "color" to defaultColor)
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildTitleOptions(titleProp: Any?): Map<String, Any?> {
        if (titleProp !is Map<*, *>) {
            return mapOf("display" to false, "text" to "")
        }

        val title = titleProp as Map<String, Any?>
        val titleStyle = title["titleStyle"] as? Map<String, Any?>
        val titleFontOptions = buildFontOptions(titleStyle)
        val titleColor =
                titleStyle?.get("textColor")?.let { ChartColorConverter.normalizeColor(it) }

        return buildMap {
            put("display", title["display"] ?: false)
            put("text", title["text"] ?: "")
            put("font", titleFontOptions)
            titleColor?.let { put("color", it) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildFontOptions(style: Map<String, Any?>?): Map<String, Any?> {
        if (style == null) return emptyMap()

        val fontStyles = mutableMapOf<String, Any?>()
        val fontToken = style["fontToken"] as? Map<String, Any?>
        val font = fontToken?.get("font") as? Map<String, Any?>

        if (font != null) {
            font["fontFamily"]?.let { fontStyles["family"] = it }
            font["size"]?.let { fontStyles["size"] = it }
            font["height"]?.let { fontStyles["lineHeight"] = it }

            // Map 'isItalic' boolean to style string
            val isItalic = font["isItalic"] as? Boolean
            fontStyles["style"] = if (isItalic == true) "italic" else "normal"

            // Map weight value
            val weightValue = font["weight"] as? String
            fontStyles["weight"] =
                    when (weightValue?.lowercase()) {
                        "bold" -> "bold"
                        "normal" -> "normal"
                        else -> weightValue?.toIntOrNull() ?: "normal"
                    }
        }

        return fontStyles
    }

    /** Validates chart types are compatible. Returns error message if invalid, null if valid. */
    @Suppress("UNCHECKED_CAST")
    fun validateChartTypes(datasets: List<Any>): String? {
        val datasetMaps = datasets.mapNotNull { it as? Map<String, Any?> }
        if (datasetMaps.isEmpty()) return null

        val radialTypes = setOf("pie", "doughnut", "polarArea", "radar")
        val cartesianTypes = setOf("line", "bar")

        val datasetTypes = datasetMaps.mapNotNull { it["type"] as? String }.toSet()

        val hasRadial = datasetTypes.any { radialTypes.contains(it) }
        val hasCartesian = datasetTypes.any { cartesianTypes.contains(it) }

        if (hasRadial && hasCartesian) {
            val radialFound = datasetTypes.filter { radialTypes.contains(it) }
            val cartesianFound = datasetTypes.filter { cartesianTypes.contains(it) }

            return "Cannot mix radial charts (${radialFound.joinToString()}) with cartesian charts (${cartesianFound.joinToString()}).\n\n" +
                    "Please use either:\n" +
                    "• Only radial charts (pie, doughnut, polarArea, radar)\n" +
                    "• Only cartesian charts (line, bar)"
        }

        // Check if mixing different radial types
        if (hasRadial && datasetTypes.size > 1) {
            val allRadial = datasetTypes.all { radialTypes.contains(it) }
            if (allRadial) {
                val uniqueRadialTypes = datasetTypes.filter { radialTypes.contains(it) }.toSet()
                if (uniqueRadialTypes.size > 1) {
                    return "Cannot mix different radial chart types (${uniqueRadialTypes.joinToString()}).\n\n" +
                            "Please use datasets of the same type."
                }
            }
        }

        return null // Valid configuration
    }
}

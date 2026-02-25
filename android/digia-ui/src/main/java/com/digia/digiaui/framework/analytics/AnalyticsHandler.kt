package com.digia.digiaui.framework.analytics

import android.content.Context
import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.framework.expression.evaluateNestedExpressions
import com.digia.digiaui.framework.logging.Logger
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.utils.asSafe

/**
 * Singleton handler for processing and executing analytics events
 *
 * Responsibilities:
 * - Processing analytics events with expression evaluation
 * - Integrating with external analytics providers
 * - Logging events for debugging purposes
 * - Managing analytics scope and context
 */
object AnalyticsHandler {
    
    /**
     * The analytics provider that will receive processed events
     * This should be set during app initialization
     */
    var analyticsProvider: DUIAnalytics? = null

    /**
     * Executes a list of analytics events with expression evaluation
     *
     * This method processes analytics events by:
     * 1. Evaluating dynamic expressions in event payloads using the provided scope
     * 2. Logging events to the Digia UI logging system for debugging
     * 3. Forwarding processed events to the registered analytics handler
     *
     * @param context Android context
     * @param events List of analytics events to process and execute
     * @param scopeContext Optional scope context for expression evaluation
     */
    fun execute(
        context: Context,
        events: List<AnalyticEvent>,
        scopeContext: ScopeContext?
    ) {
        // Evaluate expressions in event payloads and create processed events
        val evaluatedEvents = events.map { event ->
            val evaluatedPayload = event.payload?.let { payload ->
             asSafe<JsonLike>(   evaluateNestedExpressions(payload, scopeContext))
            }
            
            AnalyticEvent(
                name = event.name,
                payload = evaluatedPayload
            )
        }

        // Log events for debugging
        evaluatedEvents.forEach { event ->
            Logger.log("Analytics Event: ${event.name}, Payload: ${event.payload}")
        }

        // Forward processed events to the registered analytics handler
        analyticsProvider?.onEvent(evaluatedEvents)
    }
}

/**
 * Interface for analytics providers
 *
 * Implement this interface to integrate with third-party analytics services
 * like Firebase Analytics, Mixpanel, Amplitude, etc.
 */
interface DUIAnalytics {
    /**
     * Called when analytics events are fired
     *
     * @param events List of evaluated analytics events ready to be sent
     */
    fun onEvent(events: List<AnalyticEvent>)

    /**
     * Called when a data source request succeeds
     *
     * @param dataSourceType Type of data source (e.g., "api", "database")
     * @param source Source identifier
     * @param metaData Additional metadata about the request
     * @param perfData Performance data (timing, size, etc.)
     */
    fun onDataSourceSuccess(
        dataSourceType: String,
        source: String,
        metaData: Any?,
        perfData: Any?
    ) {}

    /**
     * Called when a data source request fails
     *
     * @param dataSourceType Type of data source (e.g., "api", "database")
     * @param source Source identifier
     * @param errorInfo Error information
     */
    fun onDataSourceError(
        dataSourceType: String,
        source: String,
        errorInfo: DataSourceErrorInfo
    ) {}
}

/**
 * Base class for data source error information
 */
open class DataSourceErrorInfo(
    val data: Any?,
    val requestOptions: Any?,
    val statusCode: Int?,
    val error: Any?,
    val message: String?
)

/**
 * Error information specific to API/server errors
 */
class ApiServerInfo(
    data: Any?,
    requestOptions: Any?,
    statusCode: Int?,
    error: Any?,
    message: String?
) : DataSourceErrorInfo(data, requestOptions, statusCode, error, message)

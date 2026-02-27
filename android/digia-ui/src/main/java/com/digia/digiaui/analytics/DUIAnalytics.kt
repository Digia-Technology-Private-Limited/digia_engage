//package com.digia.digiaui.analytics
//
///** Analytics event model mirroring the Flutter digia_ui contract. */
//data class AnalyticEvent(val name: String, val payload: Map<String, Any?>? = null) {
//    companion object {
//        fun fromJson(json: Map<String, Any?>): AnalyticEvent {
//            val name = json["name"] as? String ?: ""
//            val payload = json["payload"] as? Map<String, Any?>
//            return AnalyticEvent(name = name, payload = payload)
//        }
//    }
//
//    fun toJson(): Map<String, Any?> = mapOf("name" to name, "payload" to payload)
//}
//
///** Analytics callbacks. */
//interface DUIAnalytics {
//    fun onEvent(events: List<AnalyticEvent>)
//    fun onDataSourceSuccess(dataSourceType: String, source: String, metaData: Any?, perfData: Any?)
//
//    fun onDataSourceError(dataSourceType: String, source: String, errorInfo: DataSourceErrorInfo)
//}
//
//open class DataSourceErrorInfo(
//        val data: Any?,
//        val requestOptions: Any?,
//        val statusCode: Int?,
//        val error: Any?,
//        val message: String?
//)
//
//class ApiServerInfo(
//        data: Any?,
//        requestOptions: Any?,
//        statusCode: Int?,
//        error: Any?,
//        message: String?
//) : DataSourceErrorInfo(data, requestOptions, statusCode, error, message)

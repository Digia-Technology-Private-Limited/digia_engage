//package com.digia.digiaui.framework.models
//
//import com.digia.digiaui.framework.utils.asSafe
//
//class VWRepeatData(
//    val type: String,
//    val datum: Any?
//) {
//    val isJson: Boolean
//        get() = type == "json"
//
//    companion object {
//        fun fromJson(json: Any?): VWRepeatData? {
//            if (json == null) return null
//
//            if (json is String) {
//                return VWRepeatData(type = "json", datum = json)
//            }
//
//            if (json is Map<*, *>) {
//                if (json["expr"] != null) {
//                    return VWRepeatData(type = "object_path", datum = json["expr"])
//                }
//
//                // Fallback for legacy format
//                if (json["kind"] != null && json["datum"] != null) {
//                    return VWRepeatData(
//                        type = (asSafe<String>(json["kind"]) ?: return null) as String,
//                        datum = json["datum"]
//                    )
//                }
//            }
//
//            return null
//        }
//    }
//}
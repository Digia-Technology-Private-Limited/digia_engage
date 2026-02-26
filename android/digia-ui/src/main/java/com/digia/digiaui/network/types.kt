package com.digia.digiaui.network

enum class HttpMethod {
    GET,
    POST,
    PUT,
    DELETE,
    PATCH;

    val stringValue: String
        get() = name
}

enum class BodyType {
    JSON,
    MULTIPART,
    FORM_URLENCODED,
    GRAPHQL;

    val contentTypeHeader: String?
        get() =
                when (this) {
                    JSON -> "application/json"
                    MULTIPART -> "multipart/form-data"
                    FORM_URLENCODED -> "application/x-www-form-urlencoded"
                    GRAPHQL -> "application/json"
                }
}

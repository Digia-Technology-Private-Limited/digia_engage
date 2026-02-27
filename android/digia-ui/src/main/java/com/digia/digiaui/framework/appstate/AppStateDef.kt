package com.digia.digiaui.framework.appstate

class StateDescriptor<T>(
    val key: String,
    val initialValue: T,
    val shouldPersist: Boolean = true,
    val deserialize: (String) -> T,
    val serialize: (T) -> String,
    val description: String? = null,
    val streamName: String
)


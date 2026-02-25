package com.digia.digiaui.framework.datatype

enum class DataType(val id: String) {
    STRING("string"),
    NUMBER("number"),
    BOOLEAN("boolean"),
    JSON("json"),
    JSON_ARRAY("list"),
    SCROLL_CONTROLLER("scrollController"),
    TIMER_CONTROLLER("timerController"),
    STREAM_CONTROLLER("streamController"),
    ASYNC_CONTROLLER("asyncController"),
    TEXT_EDITING_CONTROLLER("textFieldController"),
    PAGE_CONTROLLER("pageController"),
    FILE("file"),
    API_CANCEL_TOKEN("apiCancelToken"),
    ACTION("action"),
    STORY_CONTROLLER("storyController");

    companion object {
        fun fromString(value: Any?): DataType? {
            return DataType.entries.firstOrNull { it.id == value }
        }
    }
}
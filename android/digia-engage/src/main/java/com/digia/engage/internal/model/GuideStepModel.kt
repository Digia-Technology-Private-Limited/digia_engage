package com.digia.engage.internal.model

data class GuideStepModel(
    val id: String,
    val sequenceOrder: Int,
    val anchorKey: String,
    val displayStyle: String,
    val widgetConfig: GuideStepWidgetConfig,
    val advanceTrigger: String,
    val autoDelayMs: Int?,
)

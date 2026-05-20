package com.digia.engage.internal.model

data class GuideConfigModel(
    val id: String,
    val multiStep: Boolean,
    val steps: List<GuideStepModel>,
)

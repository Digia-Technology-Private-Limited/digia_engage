package com.digia.engage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.digia.engage.internal.DigiaInstance
import com.digia.engage.internal.ui.DigiaInlineCarousel
import com.digia.engage.internal.ui.GuideRenderer

@Composable
fun DigiaHost(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        GuideRenderer()
    }
}

@Composable
fun DigiaScreen(name: String) {
    LaunchedEffect(name) { Digia.setCurrentScreen(name) }
}

@Composable
fun DigiaSlot(placementKey: String, modifier: Modifier = Modifier) {
    val slotConfigs by DigiaInstance.controller.slotConfigs.collectAsState()
    val config = slotConfigs[placementKey] ?: return
    DigiaInlineCarousel(config = config, modifier = modifier)
}

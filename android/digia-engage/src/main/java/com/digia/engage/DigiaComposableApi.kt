package com.digia.engage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
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

/** Deferred — inline campaigns not implemented in this iteration. */
@Composable
fun DigiaSlot(placementKey: String, modifier: Modifier = Modifier) {
    // No-op stub: inline campaigns are deferred post-launch.
}

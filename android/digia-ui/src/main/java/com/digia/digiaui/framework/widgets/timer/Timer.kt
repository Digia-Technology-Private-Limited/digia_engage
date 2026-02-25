package com.digia.digiaui.framework.widgets.timer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState

/**
 * Compose equivalent of the Flutter `TimerWidget`.
 *
 * - If [controller] is provided, it will be used (and not disposed by this composable).
 * - If [controller] is null, an internal [TimerController] is created and disposed automatically.
 */
@Composable
fun TimerWidget(
	controller: TimerController? = null,
	initialValue: Int = 0,
	updateIntervalMs: Long = 1000L,
	isCountDown: Boolean = false,
	duration: Int = 60,
	content: @Composable (value: Int) -> Unit
) {
	val internalController = remember(initialValue, updateIntervalMs, isCountDown, duration) {
		TimerController(
			initialValue = initialValue,
			updateInterval = updateIntervalMs,
			isCountDown = isCountDown,
			duration = duration,
		)
	}

	val actualController = controller ?: internalController

	LaunchedEffect(actualController) {
		actualController.start()
	}

	DisposableEffect(actualController, controller) {
		onDispose {
			if (controller == null) {
				actualController.dispose()
			}
		}
	}

	val value by actualController.stream.collectAsState(initial = actualController.currentValue)
	content(value)
}
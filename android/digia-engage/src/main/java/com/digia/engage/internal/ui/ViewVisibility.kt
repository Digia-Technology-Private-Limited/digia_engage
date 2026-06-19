package com.digia.engage.internal.ui

import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Tracks whether the composition's host [View] is actually visible to the user, exposed as
 * Compose state.
 *
 * [View.isShown] is the single Android-level source of truth: it is true only when this view and
 * **every** ancestor are `VISIBLE` and the view is attached to a visible window. That makes this
 * host-agnostic — it reacts the same way whether a parent is set to `GONE` (e.g.
 * react-native-screens hiding an inactive screen), the view is detached from the window, or the
 * window itself goes to the background. Any layout change in the tree (including ancestor
 * visibility flips) triggers the global layout pass we listen for, so we re-read [View.isShown]
 * and only emit when it actually changes.
 *
 * Compose's own effect APIs (`LaunchedEffect`, `DisposableEffect`) are scoped to composition, not
 * visibility — a `GONE` ancestor keeps the composition alive — so on-going work (autoplay loops,
 * video playback) needs this signal to know when to idle.
 */
@Composable
internal fun rememberIsViewOnScreen(): State<Boolean> {
    val view = LocalView.current
    val state = remember { mutableStateOf(view.isShown) }
    DisposableEffect(view) {
        val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            state.value = view.isShown
        }
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { state.value = v.isShown }
            override fun onViewDetachedFromWindow(v: View) { state.value = false }
        }
        val observer = view.viewTreeObserver
        observer.addOnGlobalLayoutListener(layoutListener)
        view.addOnAttachStateChangeListener(attachListener)
        state.value = view.isShown
        onDispose {
            if (observer.isAlive) observer.removeOnGlobalLayoutListener(layoutListener)
            view.removeOnAttachStateChangeListener(attachListener)
        }
    }
    return state
}

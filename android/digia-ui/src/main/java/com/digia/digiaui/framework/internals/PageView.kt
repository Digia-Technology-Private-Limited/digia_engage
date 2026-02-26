package com.digia.digiaui.framework.internals

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.digia.digiaui.framework.datatype.AdaptedPageController

/** Flutter-like axis for paging direction. */
enum class Axis {
	HORIZONTAL,
	VERTICAL
}

/**
 * Compose equivalent of Flutter's PageView/PageView.builder.
 *
 * - If [itemBuilder] is provided, uses builder mode and [itemCount].
 * - Otherwise uses [children].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InternalPageView(
	modifier: Modifier = Modifier,
	controller: AdaptedPageController? = null,
	scrollDirection: Axis? = null,
	physics: Any? = null,
	itemCount: Int? = null,
	reverse: Boolean? = null,
	pageSnapping: Boolean? = null,
	initialPage: Int? = null,
	viewportFraction: Float? = null,
	keepPage: Boolean? = null,
	padEnds: Boolean? = null,
	allowScroll: Boolean? = null,
	onChanged: ((Int) -> Unit)? = null,
	children: List<@Composable () -> Unit> = emptyList(),
	itemBuilder: (@Composable (index: Int) -> Unit)? = null,
) {
	val pageCount = when {
		itemBuilder != null -> itemCount ?: 0
		else -> children.size
	}.coerceAtLeast(0)

	val resolvedInitialPage = when {
		controller != null -> 0
		pageCount <= 0 -> 0
		else -> (initialPage ?: 0).coerceIn(0, pageCount - 1)
	}

	@Suppress("UNUSED_VARIABLE")
	val resolvedKeepPage = keepPage ?: true

	val pagerState = rememberPagerState(
		initialPage = resolvedInitialPage,
		pageCount = { pageCount }
	)
	LaunchedEffect(controller, pagerState) {
		controller?.attachPagerState(pagerState)
	}

	val isHorizontal = (scrollDirection ?: Axis.HORIZONTAL) == Axis.HORIZONTAL
	val reverseLayout = reverse ?: false
	val resolvedViewportFraction = (viewportFraction ?: 1f).coerceAtLeast(0.01f)
	val resolvedPadEnds = padEnds ?: true
	val userScrollEnabled = allowScroll ?: true

	// Track page changes.
	var lastReportedPage by remember { mutableIntStateOf(-1) }
	LaunchedEffect(pagerState.currentPage) {
		val current = pagerState.currentPage
		if (current != lastReportedPage) {
			lastReportedPage = current
			onChanged?.invoke(current)
		}
	}

	BoxWithConstraints(modifier = modifier) {
		val pagerModifier = if (userScrollEnabled) {
			Modifier
		} else {
			Modifier.pointerInput(Unit) {
				awaitPointerEventScope {
					while (true) {
						val event = awaitPointerEvent(pass = PointerEventPass.Initial)
						event.changes.forEach { it.consume() }
					}
				}
			}
		}

		val containerSize = if (isHorizontal) maxWidth else maxHeight
		val paddingFraction = ((1f - resolvedViewportFraction) / 2f).coerceAtLeast(0f)
		val contentPadding = if (resolvedPadEnds && paddingFraction > 0f) {
			val padding = containerSize * paddingFraction
			if (isHorizontal) PaddingValues(horizontal = padding) else PaddingValues(vertical = padding)
		} else {
			PaddingValues(0.dp)
		}

		val pageSize = PageSize.Fixed(containerSize * resolvedViewportFraction)

		if (isHorizontal) {
			HorizontalPager(
				state = pagerState,
				modifier = pagerModifier,
				contentPadding = contentPadding,
				pageSize = pageSize,
				reverseLayout = reverseLayout,
			) { page ->
				if (itemBuilder != null) {
					itemBuilder(page)
				} else {
					children.getOrNull(page)?.invoke()
				}
			}
		} else {
			VerticalPager(
				state = pagerState,
				modifier = pagerModifier,
				contentPadding = contentPadding,
				pageSize = pageSize,
				reverseLayout = reverseLayout,
			) { page ->
				if (itemBuilder != null) {
					itemBuilder(page)
				} else {
					children.getOrNull(page)?.invoke()
				}
			}
		}
	}
}

/**
 * Kept for backwards-compatibility with earlier internal references.
 * Prefer using [InternalPageView].
 */
@Deprecated("Use InternalPageView composable")
class PageView

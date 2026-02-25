package com.digia.digiaui.framework.widgets

import LocalUIResources
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.actions.LocalActionExecutor
import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.base.VirtualCompositeNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.color
import com.digia.digiaui.framework.expr.DefaultScopeContext
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.registerAllChildern
import com.digia.digiaui.framework.state.LocalStateContextProvider
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.framework.utils.NumUtil
import com.digia.digiaui.framework.utils.toDp
import com.digia.digiaui.framework.utils.toPercentFraction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/** Carousel widget properties matching the Flutter/Dart schema */
data class CarouselProps(
    val width: String? = null,
    val height: String? = null,
    val direction: String = "horizontal",
    val aspectRatio: Double = 0.25,
    val initialPage: ExprOr<Int>? = null,
    val enlargeCenterPage: Boolean = false,
    val viewportFraction: Double = 0.8,
    val autoPlay: Boolean = false,
    val animationDuration: Int = 800,
    val autoPlayInterval: Int = 1600,
    val infiniteScroll: Boolean = false,
    val reverseScroll: Boolean = false,
    val enlargeFactor: Double = 0.3,
    val pageSnapping: Boolean = true,
    val padEnds: Boolean = true,
    val keepAlive: Boolean = false,
    // Indicator props
    val showIndicator: Boolean = false,
    val offset: Double = 16.0,
    val dotHeight: Double = 10.0,
    val dotWidth: Double = 10.0,
    val spacing: Double = 4.0,
    val dotColor: ExprOr<String>? = null,
    val activeDotColor: ExprOr<String>? = null,
    val indicatorEffectType: String = "expanding",
    // Events & Data
    val onChanged: ActionFlow? = null,
    val dataSource: Any? = null
) {
    companion object {
        fun fromJson(json: JsonLike): CarouselProps {
            val indicatorJson = (json["indicator"] as? Map<*, *>)
                ?.get("indicatorAvailable") as? Map<*, *> ?: emptyMap<String, Any>()

            return CarouselProps(
                width = json["width"] as? String,
                height = json["height"] as? String,
                direction = (json["direction"] as? String) ?: "horizontal",
                aspectRatio = NumUtil.toDouble(json["aspectRatio"]) ?: 0.25,
                initialPage = ExprOr.fromValue(json["initialPage"]),
                enlargeCenterPage = (json["enlargeCenterPage"] as? Boolean) ?: false,
                viewportFraction = NumUtil.toDouble(json["viewportFraction"]) ?: 0.8,
                autoPlay = (json["autoPlay"] as? Boolean) ?: false,
                animationDuration = (json["animationDuration"] as? Number)?.toInt() ?: 800,
                autoPlayInterval = (json["autoPlayInterval"] as? Number)?.toInt() ?: 1600,
                infiniteScroll = (json["infiniteScroll"] as? Boolean) ?: false,
                reverseScroll = (json["reverseScroll"] as? Boolean) ?: false,
                enlargeFactor = NumUtil.toDouble(json["enlargeFactor"]) ?: 0.3,
                pageSnapping = (json["pageSnapping"] as? Boolean) ?: true,
                padEnds = (json["padEnds"] as? Boolean) ?: true,
                keepAlive = (json["keepAlive"] as? Boolean) ?: false,
                // Indicator
                showIndicator = (indicatorJson["showIndicator"] as? Boolean) ?: false,
                offset = NumUtil.toDouble(indicatorJson["offset"]) ?: 16.0,
                dotHeight = NumUtil.toDouble(indicatorJson["dotHeight"]) ?: 10.0,
                dotWidth = NumUtil.toDouble(indicatorJson["dotWidth"]) ?: 10.0,
                spacing = NumUtil.toDouble(indicatorJson["spacing"]) ?: 4.0,
                dotColor = ExprOr.fromValue(indicatorJson["dotColor"]),
                activeDotColor = ExprOr.fromValue(indicatorJson["activeDotColor"]),
                indicatorEffectType = (indicatorJson["indicatorEffectType"] as? String) ?: "expanding",
                // Events & Data
                onChanged = ActionFlow.fromJson(json["onChanged"] as? JsonLike),
                dataSource = json["dataSource"]
            )
        }
    }
}

/** Virtual Widget for Carousel rendering */
class VWCarousel(
    refName: String? = null,
    commonProps: CommonProps? = null,
    props: CarouselProps,
    parent: VirtualNode? = null,
    slots: ((VirtualCompositeNode<CarouselProps>) -> Map<String, List<VirtualNode>>?)? = null,
    parentProps: Props? = null
) : VirtualCompositeNode<CarouselProps>(
    props = props,
    commonProps = commonProps,
    parentProps = parentProps,
    parent = parent,
    refName = refName,
    _slots = slots
) {

    private val shouldRepeatChild: Boolean
        get() = props.dataSource != null

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun Render(payload: RenderPayload) {
        val context = LocalContext.current.applicationContext
        val resources = LocalUIResources.current
        val stateContext = LocalStateContextProvider.current
        val actionExecutor = LocalActionExecutor.current
        val scope = rememberCoroutineScope()

        if (child == null) {
            Empty()
            return
        }

        // Resolve items from dataSource
        val items = if (shouldRepeatChild) {
            payload.eval<List<Any>>(props.dataSource) ?: emptyList()
        } else {
            listOf(Unit) // Single child mode
        }

        if (items.isEmpty()) {
            Empty()
            return
        }

        val initialPage = payload.evalExpr(props.initialPage) ?: 0
        val realItemCount = items.size
        // Infinite scroll: use large page count with modular indexing
        val pageCount = if (props.infiniteScroll) Int.MAX_VALUE else realItemCount
        val actualInitialPage = if (props.infiniteScroll) {
            (Int.MAX_VALUE / 2) - ((Int.MAX_VALUE / 2) % realItemCount) + initialPage.coerceIn(0, realItemCount - 1)
        } else {
            initialPage.coerceIn(0, realItemCount - 1)
        }

        val pagerState = rememberPagerState(
            initialPage = actualInitialPage,
            pageCount = { pageCount }
        )

        // Track page changes for onChanged callback
        var lastReportedPage by remember { mutableIntStateOf(-1) }
        LaunchedEffect(pagerState.currentPage) {
            val realPage = if (props.infiniteScroll) {
                pagerState.currentPage % realItemCount
            } else {
                pagerState.currentPage
            }
            if (realPage != lastReportedPage) {
                lastReportedPage = realPage
                props.onChanged?.let { actionFlow ->
                    payload.executeAction(
                        context = context,
                        actionFlow = actionFlow,
                        actionExecutor = actionExecutor,
                        stateContext = stateContext,
                        resourcesProvider = resources,
                        incomingScopeContext = DefaultScopeContext(variables = mapOf("index" to realPage))
                    )
                }
            }
        }

        // Auto-play logic with configurable interval and animation duration
        if (props.autoPlay && realItemCount > 1) {
            LaunchedEffect(pagerState, props.autoPlayInterval, props.animationDuration) {
                while (true) {
                    delay(props.autoPlayInterval.toLong())
                    val nextPage = pagerState.currentPage + 1
                    pagerState.animateScrollToPage(
                        page = nextPage,
                        animationSpec = tween(durationMillis = props.animationDuration)
                    )
                }
            }
        }

        // Sizing: height takes priority over aspectRatio (per Flutter behavior)
        val widthDp: Dp? = props.width?.toDp()
        val widthPercent = props.width?.toPercentFraction()
        val heightDp: Dp? = props.height?.toDp()
        val heightPercent = props.height?.toPercentFraction()

        var modifier = Modifier.buildModifier(payload)
        modifier = modifier.then(
            when {
                widthPercent != null -> Modifier.fillMaxWidth(widthPercent)
                widthDp != null -> Modifier.width(widthDp)
                else -> Modifier.fillMaxWidth()
            }
        )
        modifier = modifier.then(
            when {
                heightPercent != null -> Modifier.fillMaxHeight(heightPercent)
                heightDp != null -> Modifier.height(heightDp)
                props.aspectRatio > 0 -> Modifier.aspectRatio(1f / props.aspectRatio.toFloat())
                else -> Modifier.height(300.dp) // Fallback
            }
        )

        val isHorizontal = props.direction.lowercase() != "vertical"

        // Resolve indicator colors
        val dotColor = payload.color(payload.evalExpr(props.dotColor) ?: "contentTertiary") ?: Color.Gray
        val activeDotColor = payload.color(payload.evalExpr(props.activeDotColor) ?: "brandPrimary") ?: Color.Blue

        // Calculate content padding for padEnds (viewport fraction effect)
        val paddingFraction = ((1f - props.viewportFraction.toFloat()) / 2f)

        Column(modifier = modifier) {
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val containerSize = if (isHorizontal) maxWidth else maxHeight
                val contentPadding = if (props.padEnds) {
                    val padding = containerSize * paddingFraction
                    if (isHorizontal) PaddingValues(horizontal = padding) 
                    else PaddingValues(vertical = padding)
                } else {
                    PaddingValues(0.dp)
                }

                // Page size based on viewport fraction
                val pageSize = PageSize.Fixed(containerSize * props.viewportFraction.toFloat())

                if (isHorizontal) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = contentPadding,
                        pageSize = pageSize,
                        pageSpacing = 0.dp,
                        reverseLayout = props.reverseScroll,
                        beyondViewportPageCount = if (props.keepAlive) 2 else 0,
                        key = { page -> if (props.infiniteScroll) page else page % realItemCount }
                    ) { page ->
                        CarouselPage(
                            page = page,
                            pagerState = pagerState,
                            items = items,
                            realItemCount = realItemCount,
                            isInfiniteScroll = props.infiniteScroll,
                            enlargeCenterPage = props.enlargeCenterPage,
                            enlargeFactor = props.enlargeFactor,
                            shouldRepeatChild = shouldRepeatChild,
                            payload = payload
                        )
                    }
                } else {
                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = contentPadding,
                        pageSize = pageSize,
                        pageSpacing = 0.dp,
                        reverseLayout = props.reverseScroll,
                        beyondViewportPageCount = if (props.keepAlive) 2 else 0,
                        key = { page -> if (props.infiniteScroll) page else page % realItemCount }
                    ) { page ->
                        CarouselPage(
                            page = page,
                            pagerState = pagerState,
                            items = items,
                            realItemCount = realItemCount,
                            isInfiniteScroll = props.infiniteScroll,
                            enlargeCenterPage = props.enlargeCenterPage,
                            enlargeFactor = props.enlargeFactor,
                            shouldRepeatChild = shouldRepeatChild,
                            payload = payload
                        )
                    }
                }
            }

            // Indicator
            if (props.showIndicator && realItemCount > 1) {
                Spacer(modifier = Modifier.height(props.offset.dp))
                PagerIndicator(
                    pagerState = pagerState,
                    pageCount = realItemCount,
                    isInfinite = props.infiniteScroll,
                    dotWidth = props.dotWidth.dp,
                    dotHeight = props.dotHeight.dp,
                    spacing = props.spacing.dp,
                    dotColor = dotColor,
                    activeDotColor = activeDotColor,
                    effectType = props.indicatorEffectType,
                    onDotClick = { index ->
                        scope.launch {
                            val targetPage = if (props.infiniteScroll) {
                                val currentBase = pagerState.currentPage - (pagerState.currentPage % realItemCount)
                                currentBase + index
                            } else index
                            pagerState.animateScrollToPage(
                                page = targetPage,
                                animationSpec = tween(durationMillis = props.animationDuration)
                            )
                        }
                    }
                )
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun CarouselPage(
        page: Int,
        pagerState: PagerState,
        items: List<Any>,
        realItemCount: Int,
        isInfiniteScroll: Boolean,
        enlargeCenterPage: Boolean,
        enlargeFactor: Double,
        shouldRepeatChild: Boolean,
        payload: RenderPayload
    ) {
        val realIndex = if (isInfiniteScroll) page % realItemCount else page
        val item = items[realIndex]
        val scopedPayload = if (shouldRepeatChild) {
            payload.copyWithChainedContext(createExprContext(item, realIndex))
        } else payload

        // Calculate scale for enlarge center page effect
        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
        val scale = if (enlargeCenterPage) {
            val factor = enlargeFactor.toFloat()
            1f - (pageOffset.coerceIn(0f, 1f) * factor)
        } else 1f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center
        ) {
            child?.ToWidget(scopedPayload)
        }
    }

    private fun createExprContext(item: Any?, index: Int): DefaultScopeContext {
        val carouselObj = mapOf(
            "currentItem" to item,
            "index" to index
        )
        val variables = mutableMapOf<String, Any?>().apply {
            putAll(carouselObj)
            refName?.let { put(it, carouselObj) }
        }
        return DefaultScopeContext(variables = variables)
    }
}

/** Pager indicator with multiple effect types */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PagerIndicator(
    pagerState: PagerState,
    pageCount: Int,
    isInfinite: Boolean,
    dotWidth: Dp,
    dotHeight: Dp,
    spacing: Dp,
    dotColor: Color,
    activeDotColor: Color,
    effectType: String,
    onDotClick: (Int) -> Unit
) {
    val currentPage = if (isInfinite) pagerState.currentPage % pageCount else pagerState.currentPage
    val pageOffset = pagerState.currentPageOffsetFraction

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isActive = index == currentPage
            val isNextActive = index == (currentPage + 1) % pageCount

            // Calculate animated width/color based on effect type
            val (animatedWidth, animatedColor) = when (effectType.lowercase()) {
                "expanding" -> {
                    val expandedWidth = dotWidth * 2f
                    val width = when {
                        isActive -> dotWidth + (expandedWidth - dotWidth) * (1f - pageOffset.absoluteValue)
                        isNextActive && pageOffset > 0 -> dotWidth + (expandedWidth - dotWidth) * pageOffset
                        else -> dotWidth
                    }
                    val color = if (isActive || (isNextActive && pageOffset > 0.5f)) activeDotColor else dotColor
                    width to color
                }
                "scale" -> {
                    val scaleWidth = if (isActive) dotWidth * 1.5f else dotWidth
                    scaleWidth to (if (isActive) activeDotColor else dotColor)
                }
                "worm", "slide" -> {
                    // Simple dot, color transitions
                    dotWidth to (if (isActive) activeDotColor else dotColor)
                }
                else -> dotWidth to (if (isActive) activeDotColor else dotColor)
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = spacing / 2)
                    .width(animatedWidth)
                    .height(dotHeight)
                    .clip(CircleShape)
                    .background(animatedColor)
                    .clickable { onDotClick(index) }
            )
        }
    }
}

/** Builder function for Carousel widget */
fun carouselBuilder(
    data: VWNodeData,
    parent: VirtualNode?,
    registry: VirtualWidgetRegistry
): VirtualNode {
    val childGroups = data.childGroups?.mapValues { (_, childrenData) ->
        childrenData.map { registry.createWidget(it, parent) }
    }

    return VWCarousel(
        refName = data.refName,
        commonProps = data.commonProps,
        parent = parent,
        parentProps = data.parentProps,
        props = CarouselProps.fromJson(data.props.value),
        slots = {
                self ->
            registerAllChildern(data.childGroups, self, registry)
        },
    )
}
package com.digia.engage.internal.ui

import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.digia.engage.internal.DigiaInstance
import com.digia.engage.internal.interpolate
import com.digia.engage.internal.model.NudgeBox
import com.digia.engage.internal.model.NudgeButton
import com.digia.engage.internal.model.NudgeButtonVariant
import com.digia.engage.internal.model.NudgeCarousel
import com.digia.engage.internal.model.NudgeDivider
import com.digia.engage.internal.model.NudgeGap
import com.digia.engage.internal.model.NudgeImage
import com.digia.engage.internal.model.NudgeLottie
import com.digia.engage.internal.model.NudgeNode
import com.digia.engage.internal.model.NudgeText
import com.digia.engage.internal.model.NudgeTextAlign
import com.digia.engage.internal.model.NudgeVideo

/** Variables available to all nudge node renderers — mirroring Flutter's VariableScopeProvider. */
internal val LocalNudgeVariables = compositionLocalOf<Map<String, String>?> { null }

@Composable
internal fun NudgeNodeRenderer(node: NudgeNode) {
    val vars = LocalNudgeVariables.current
    NudgeBoxDecorator(box = node.box) {
        when (node) {
            is NudgeText -> NudgeTextNode(node, vars)
            is NudgeImage -> NudgeImageNode(node, vars)
            is NudgeButton -> NudgeButtonNode(node, vars)
            is NudgeGap -> Spacer(Modifier.height(node.height.dp))
            is NudgeDivider -> NudgeDividerNode(node)
            is NudgeLottie -> NudgeLottieNode(node, vars)
            is NudgeCarousel -> NudgeCarouselNode(node, vars)
            is NudgeVideo -> NudgeVideoNode(node, vars)
        }
    }
}

@Composable
private fun NudgeTextNode(node: NudgeText, vars: Map<String, String>?) {
    val text = interpolate(node.text, vars)
    val weight = when (node.fontWeight) {
        500 -> FontWeight.W500; 600 -> FontWeight.W600; 700 -> FontWeight.W700
        else -> FontWeight.W400
    }
    val align = when (node.align) {
        NudgeTextAlign.CENTER -> TextAlign.Center
        NudgeTextAlign.RIGHT -> TextAlign.Right
        else -> TextAlign.Left
    }
    Text(
        text = text,
        fontSize = node.fontSize.sp,
        fontWeight = weight,
        color = Color(node.color),
        textAlign = align,
    )
}

@Composable
private fun NudgeImageNode(node: NudgeImage, vars: Map<String, String>?) {
    val url = interpolate(node.url, vars)
    val scale = when (node.fit) {
        "contain" -> ContentScale.Fit; "fill" -> ContentScale.FillBounds; else -> ContentScale.Crop
    }
    if (url.isEmpty()) {
        NudgePlaceholder(label = "No image URL", height = node.box.fixedHeight ?: 120f)
        return
    }
    if (node.aspectRatio > 0f) {
        AsyncImage(
            model = url, contentDescription = null, contentScale = scale,
            modifier = Modifier.fillMaxWidth().aspectRatio(node.aspectRatio),
        )
    } else {
        val mod = if (node.box.fillWidth) Modifier.fillMaxWidth() else Modifier
        AsyncImage(
            model = url, contentDescription = null, contentScale = scale,
            modifier = node.box.fixedHeight?.let { mod.height(it.dp) } ?: mod,
        )
    }
}

@Composable
private fun NudgeButtonNode(node: NudgeButton, vars: Map<String, String>?) {
    val label = interpolate(node.label, vars)
    val weight = when (node.fontWeight) {
        600 -> FontWeight.W600; 700 -> FontWeight.W700; else -> FontWeight.W400
    }
    val isFilled = node.variant == NudgeButtonVariant.FILL || node.variant == NudgeButtonVariant.ELEVATED
    val bg = if (isFilled) Color(node.background) else Color.Transparent
    val fg = if (isFilled) Color(node.textColor) else Color(node.background)
    val shape = RoundedCornerShape(node.radius.dp)
    val mod = if (node.box.fillWidth) Modifier.fillMaxWidth() else Modifier

    Surface(
        color = bg,
        shape = shape,
        shadowElevation = if (node.variant == NudgeButtonVariant.ELEVATED) 3.dp else 0.dp,
        modifier = mod.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) {
            if (node.isPrimary) DigiaInstance.emitNudgeClick(null)
        },
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            fontSize = node.fontSize.sp,
            fontWeight = weight,
            color = fg,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NudgeDividerNode(node: NudgeDivider) {
    HorizontalDivider(
        modifier = Modifier.padding(start = node.indent.dp, end = node.endIndent.dp),
        thickness = node.thickness.dp,
        color = Color(node.color),
    )
}

@Composable
private fun NudgeLottieNode(node: NudgeLottie, vars: Map<String, String>?) {
    val url = interpolate(node.url, vars)
    if (url.isEmpty()) {
        NudgePlaceholder(label = "No Lottie URL", height = node.height)
        return
    }
    val composition by rememberLottieComposition(LottieCompositionSpec.Url(url))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = node.autoplay,
        iterations = if (node.loop) LottieConstants.IterateForever else 1,
    )
    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(node.height.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NudgeCarouselNode(node: NudgeCarousel, vars: Map<String, String>?) {
    val images = node.images.map { interpolate(it, vars) }.filter { it.isNotEmpty() }
    if (images.isEmpty()) {
        NudgePlaceholder(label = "No images", height = node.height)
        return
    }
    val pagerState = rememberPagerState { if (node.loop) Int.MAX_VALUE else images.size }

    // Auto-play
    if (node.autoPlay && images.size > 1) {
        LaunchedEffect(pagerState) {
            while (true) {
                kotlinx.coroutines.delay(node.autoPlayInterval.toLong())
                val next = (pagerState.currentPage + 1) % (if (node.loop) images.size else images.size)
                pagerState.animateScrollToPage(
                    if (node.loop) pagerState.currentPage + 1 else next
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth().height(node.height.dp)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
            val realPage = if (node.loop) page % images.size else page
            AsyncImage(
                model = images[realPage],
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            )
        }
        if (node.showIndicator) {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            ) {
                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                    repeat(images.size) { i ->
                        val isCurrent = (pagerState.currentPage % images.size) == i
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .then(if (isCurrent) Modifier.fillMaxWidth(0.04f) else Modifier.fillMaxWidth(0.02f))
                                .clip(CircleShape)
                                .background(if (isCurrent) Color(0xFF4945FF) else Color(0xFFCBD5E1)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NudgeVideoNode(node: NudgeVideo, vars: Map<String, String>?) {
    val url = interpolate(node.url, vars)
    if (url.isEmpty()) {
        NudgePlaceholder(label = "No video URL", height = node.height)
        return
    }
    val context = LocalContext.current
    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = if (node.loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            playWhenReady = node.autoplay
            prepare()
        }
    }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(node.height.dp)
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    player = exoPlayer
                    useController = node.showControls
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        )
    }
}

@Composable
private fun NudgePlaceholder(label: String, height: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .background(Color(0xFFF1F1F5)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = 11.sp, color = Color(0xFF9A9AAD))
    }
}

package com.digia.engage.internal.ui.nudge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.digia.engage.DigiaExperienceEvent
import com.digia.engage.internal.DigiaFontConfig
import com.digia.engage.internal.DigiaInstance
import com.digia.engage.internal.interpolate
import com.digia.engage.internal.logging.Logger
import com.digia.engage.internal.model.CopyToClipboardAction
import com.digia.engage.internal.model.DismissAction
import com.digia.engage.internal.model.NudgeBox
import com.digia.engage.internal.model.NudgeButton
import com.digia.engage.internal.model.NudgeButtonVariant
import com.digia.engage.internal.model.NudgeCarousel
import com.digia.engage.internal.model.NudgeColumn
import com.digia.engage.internal.model.NudgeDivider
import com.digia.engage.internal.model.NudgeGap
import com.digia.engage.internal.model.NudgeImage
import com.digia.engage.internal.model.NudgeLottie
import com.digia.engage.internal.model.NudgeNode
import com.digia.engage.internal.model.NudgeSelfAlign
import com.digia.engage.internal.model.NudgeText
import com.digia.engage.internal.model.NudgeTextAlign
import com.digia.engage.internal.model.NudgeVideo
import com.digia.engage.internal.model.OpenDeeplinkAction
import com.digia.engage.internal.model.OpenUrlAction
import com.digia.engage.internal.model.ShareAction
import kotlinx.coroutines.delay

/// Carries the active nudge's variables down the render tree so each leaf can
/// interpolate `{{ placeholder }}` copy at draw time, without threading the map
/// through every widget. Mirrors Flutter's `VariableScopeProvider`. Null/empty
/// means every placeholder collapses to empty (see [interpolate]).
internal val LocalNudgeVariables = compositionLocalOf<Map<String, String>?> { null }

@Composable
internal fun NudgeColumnContent(
    column: NudgeColumn,
    onDismiss: () -> Unit,
) {
    Column(
        verticalArrangement = column.mainAxisAlignment.toMainAxisArrangement(column.spacing),
        horizontalAlignment = column.crossAxisAlignment.toCrossAxisAlignment(),
    ) {
        column.children.forEach { node ->
            NudgeNodeItem(node, onDismiss, column.crossAxisAlignment)
        }
    }
}

@Composable
private fun ColumnScope.NudgeNodeItem(
    node: NudgeNode,
    onDismiss: () -> Unit,
    parentCrossAxis: String,
) {
    val selfAlign = node.box.selfAlign
    val alignMod = if (selfAlign != null) Modifier.align(selfAlign.toHorizontalAlignment())
                   else Modifier

    Box(modifier = alignMod.nudgeBox(node.box)) {
        when (node) {
            is NudgeText -> NudgeTextWidget(node)
            is NudgeImage -> NudgeImageWidget(node)
            is NudgeButton -> NudgeButtonWidget(node, onDismiss)
            is NudgeGap -> Spacer(Modifier.height(node.height.dp))
            is NudgeDivider -> NudgeDividerWidget(node)
            is NudgeLottie -> NudgeLottieWidget(node)
            is NudgeCarousel -> NudgeCarouselWidget(node)
            is NudgeVideo -> NudgeVideoWidget(node)
        }
    }
}

// ─── text ─────────────────────────────────────────────────────────────────────

@Composable
private fun NudgeTextWidget(node: NudgeText) {
    Text(
        text = interpolate(node.text, LocalNudgeVariables.current),
        textAlign = node.align.toTextAlign(),
        style = TextStyle(
            fontSize = node.fontSize.sp,
            fontWeight = FontWeight(node.fontWeight),
            color = Color(node.color),
            fontFamily = DigiaFontConfig.composeFontFamily(),
        ),
        modifier = if (node.box.fillWidth) Modifier.fillMaxWidth() else Modifier,
    )
}

// ─── image ────────────────────────────────────────────────────────────────────

@Composable
private fun NudgeImageWidget(node: NudgeImage) {
    val url = interpolate(node.url, LocalNudgeVariables.current)
    if (url.isEmpty()) {
        NudgePlaceholder("Image", node.box.fixedHeight ?: 120f)
        return
    }
    val errorHeight = node.box.fixedHeight ?: 120f

    val context = LocalContext.current

    // Aspect ratio (when set) drives the height; otherwise use the box's fixed
    // size, then natural size. Mirrors the Flutter renderer.
    val modifier = if (node.aspectRatio > 0f) {
        (if (node.box.fillWidth) Modifier.fillMaxWidth() else Modifier)
            .aspectRatio(node.aspectRatio)
    } else {
        val widthMod = when {
            node.box.fixedWidth != null -> Modifier.width(node.box.fixedWidth.dp)
            else -> Modifier.fillMaxWidth()
        }
        if (node.box.fixedHeight != null) widthMod.height(node.box.fixedHeight.dp) else widthMod
    }

    SubcomposeAsyncImage(
        model = nudgeImageRequest(context, url),
        contentDescription = null,
        contentScale = node.fit.toContentScale(),
        modifier = modifier,
    ) {
        val state = painter.state
        if (state is AsyncImagePainter.State.Error) {
            Logger.error("Nudge image load failed url=$url", error = state.result.throwable)
            NudgePlaceholder("Image failed", errorHeight)
        } else {
            SubcomposeAsyncImageContent()
        }
    }
}

private const val NUDGE_IMAGE_USER_AGENT = "DigiaEngage/1.0 (Android)"

private fun nudgeImageRequest(context: Context, url: String): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .setHeader("User-Agent", NUDGE_IMAGE_USER_AGENT)
        .crossfade(true)
        .build()

// ─── button ───────────────────────────────────────────────────────────────────

@Composable
private fun NudgeButtonWidget(node: NudgeButton, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val filled = node.variant == NudgeButtonVariant.FILL || node.variant == NudgeButtonVariant.ELEVATED
    val background = if (filled) Color(node.background) else Color.Transparent
    val foreground = if (filled) Color(node.textColor) else Color(node.background)
    val elevation = if (node.variant == NudgeButtonVariant.ELEVATED) 3.dp else 0.dp

    val variables = LocalNudgeVariables.current
    // The outer nudgeBox constrains the wrapping Box to fixedWidth/fixedHeight.
    // The Surface must fill that space; otherwise it defaults to wrap-content.
    val widthMod = when {
        node.box.fillWidth || node.box.fixedWidth != null -> Modifier.fillMaxWidth()
        else -> Modifier
    }
    val heightMod = if (node.box.fixedHeight != null) Modifier.fillMaxHeight() else Modifier
    val borderMod = if (node.variant == NudgeButtonVariant.OUTLINE)
        Modifier.border(1.5.dp, Color(node.background), RoundedCornerShape(node.radius.dp))
    else Modifier

    Surface(
        color = background,
        shadowElevation = elevation,
        shape = RoundedCornerShape(node.radius.dp),
        modifier = widthMod.then(heightMod).then(borderMod),
    ) {
        Box(
            modifier = Modifier
                .clickable(
                    indication = ripple(),
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    // Any actionable button (a primary CTA, or any button carrying
                    // actions) is a call-to-action: emit the coarse Clicked to CEP and
                    // the rich `Digia Experience Clicked` matrix event to Digia, then
                    // run its actions. Mirrors the Flutter NudgeButton renderer.
                    if (node.isPrimary || node.actions.isNotEmpty()) {
                        DigiaInstance.emitNudgeClick(
                            label = node.label,
                            isPrimary = node.isPrimary,
                            actions = node.actions,
                        )
                    }
                    node.actions.forEach { action ->
                        when (action) {
                            is DismissAction -> onDismiss()
                            is OpenUrlAction -> runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(
                                        interpolate(action.url, variables)
                                    )).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                            is OpenDeeplinkAction -> runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(
                                        interpolate(action.url, variables)
                                    )).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                            is CopyToClipboardAction -> runCatching {
                                val clipboard = context
                                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText(
                                        "Digia Engage", interpolate(action.text, variables)
                                    )
                                )
                            }
                            is ShareAction -> runCatching {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, interpolate(action.text, variables))
                                }
                                context.startActivity(
                                    Intent.createChooser(send, null)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = interpolate(node.label, LocalNudgeVariables.current),
                style = TextStyle(
                    color = foreground,
                    fontSize = node.fontSize.sp,
                    fontWeight = FontWeight(node.fontWeight),
                    fontFamily = DigiaFontConfig.composeFontFamily(),
                ),
            )
        }
    }
}

// ─── divider ──────────────────────────────────────────────────────────────────

@Composable
private fun NudgeDividerWidget(node: NudgeDivider) {
    Row(modifier = Modifier.fillMaxWidth()) {
        if (node.indent > 0f) Spacer(Modifier.width(node.indent.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(node.thickness.dp)
                .background(Color(node.color)),
        )
        if (node.endIndent > 0f) Spacer(Modifier.width(node.endIndent.dp))
    }
}

// ─── lottie ───────────────────────────────────────────────────────────────────

@Composable
private fun NudgeLottieWidget(node: NudgeLottie) {
    val url = interpolate(node.url, LocalNudgeVariables.current)
    if (url.isEmpty()) {
        NudgePlaceholder("Lottie", node.height)
        return
    }
    val composition by rememberLottieComposition(LottieCompositionSpec.Url(url))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = if (node.loop) LottieConstants.IterateForever else 1,
        isPlaying = node.autoplay,
    )
    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(node.height.dp),
    )
}

// ─── carousel (image pager) ───────────────────────────────────────────────────

@Composable
private fun NudgeCarouselWidget(node: NudgeCarousel) {
    val context = LocalContext.current
    val vars = LocalNudgeVariables.current
    val images = node.images.map { interpolate(it, vars) }.filter { it.isNotEmpty() }
    if (images.isEmpty()) {
        NudgePlaceholder("No images", node.height)
        return
    }
    val pagerState = rememberPagerState(pageCount = {
        if (node.loop) Int.MAX_VALUE else images.size
    })
    val pageCount = images.size
    val cornerRadius = node.box.borderRadius

    if (node.autoPlay && node.images.size > 1) {
        LaunchedEffect(pagerState) {
            while (true) {
                delay(node.autoPlayInterval.toLong())
                val next = pagerState.currentPage + 1
                pagerState.animateScrollToPage(if (node.loop) next else next.coerceAtMost(pageCount - 1))
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(node.height.dp),
            contentPadding = PaddingValues(0.dp),
        ) { page ->
            val realIndex = page % pageCount
            SubcomposeAsyncImage(
                model = nudgeImageRequest(context, images[realIndex]),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (cornerRadius > 0f)
                            Modifier.clip(RoundedCornerShape(cornerRadius.dp))
                        else Modifier
                    ),
            ) { SubcomposeAsyncImageContent() }
        }
        if (node.showIndicator && pageCount > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                val currentIndex = pagerState.currentPage % pageCount
                repeat(pageCount) { i ->
                    val isActive = i == currentIndex
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (isActive) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) Color(0xFF4945FF.toInt()) else Color(0xFFCBD5E1.toInt())
                            ),
                    )
                }
            }
        }
    }
}

// ─── video ────────────────────────────────────────────────────────────────────

@OptIn(UnstableApi::class)
@Composable
private fun NudgeVideoWidget(node: NudgeVideo) {
    val url = interpolate(node.url, LocalNudgeVariables.current)
    if (url.isEmpty()) {
        NudgePlaceholder("No video URL", node.height)
        return
    }
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = node.autoplay
            repeatMode = if (node.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = if (node.muted) 0f else 1f
        }
    }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    val cornerRadius = node.box.borderRadius
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(node.height.dp)
            .background(Color.Black)
            .then(
                if (cornerRadius > 0f) Modifier.clip(RoundedCornerShape(cornerRadius.dp)) else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = node.showControls
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// ─── placeholder ─────────────────────────────────────────────────────────────

@Composable
private fun NudgePlaceholder(label: String, height: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .background(Color(0xFFF1F1F5.toInt()), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TextStyle(fontSize = 11.sp, color = Color(0xFF9A9AAD.toInt())))
    }
}

// ─── modifier helpers ─────────────────────────────────────────────────────────

private fun Modifier.nudgeBox(box: NudgeBox): Modifier {
    var mod = this
    if (box.marginLeft > 0f || box.marginTop > 0f || box.marginRight > 0f || box.marginBottom > 0f) {
        mod = mod.padding(
            start = box.marginLeft.dp,
            top = box.marginTop.dp,
            end = box.marginRight.dp,
            bottom = box.marginBottom.dp,
        )
    }
    if (box.background != null) {
        val color = Color(box.background)
        val shape = if (box.borderRadius > 0f) RoundedCornerShape(box.borderRadius.dp) else null
        mod = if (shape != null) mod.background(color, shape) else mod.background(color)
    }
    // Clip content to border radius so child views (e.g. images) respect rounded
    // corners — matches iOS clipShape and Flutter's Container clip behaviour.
    if (box.borderRadius > 0f) {
        mod = mod.clip(RoundedCornerShape(box.borderRadius.dp))
    }
    if (box.borderColor != null && box.borderWidth > 0f) {
        val shape = RoundedCornerShape(box.borderRadius.dp)
        mod = mod.border(box.borderWidth.dp, Color(box.borderColor), shape)
    }
    if (box.paddingLeft > 0f || box.paddingTop > 0f || box.paddingRight > 0f || box.paddingBottom > 0f) {
        mod = mod.padding(
            start = box.paddingLeft.dp,
            top = box.paddingTop.dp,
            end = box.paddingRight.dp,
            bottom = box.paddingBottom.dp,
        )
    }
    if (box.fillWidth) mod = mod.fillMaxWidth()
    if (box.fixedWidth != null) mod = mod.width(box.fixedWidth.dp)
    if (box.fixedHeight != null) mod = mod.height(box.fixedHeight.dp)
    return mod
}

// ─── type converters ──────────────────────────────────────────────────────────

private fun String.toCrossAxisAlignment(): Alignment.Horizontal = when (this) {
    "center" -> Alignment.CenterHorizontally
    "end" -> Alignment.End
    else -> Alignment.Start
}

private fun String.toMainAxisArrangement(spacing: Float): Arrangement.Vertical = when (this) {
    "center" -> Arrangement.Center
    "end" -> Arrangement.Bottom
    "spaceBetween" -> Arrangement.SpaceBetween
    "spaceAround" -> Arrangement.SpaceAround
    "spaceEvenly" -> Arrangement.SpaceEvenly
    else -> if (spacing > 0f) Arrangement.spacedBy(spacing.dp) else Arrangement.Top
}

private fun NudgeSelfAlign.toHorizontalAlignment(): Alignment.Horizontal = when (this) {
    NudgeSelfAlign.CENTER -> Alignment.CenterHorizontally
    NudgeSelfAlign.END -> Alignment.End
    NudgeSelfAlign.START -> Alignment.Start
}

private fun NudgeTextAlign.toTextAlign(): TextAlign = when (this) {
    NudgeTextAlign.CENTER -> TextAlign.Center
    NudgeTextAlign.RIGHT -> TextAlign.End
    NudgeTextAlign.LEFT -> TextAlign.Start
}

private fun String.toContentScale(): ContentScale = when (this) {
    "fill" -> ContentScale.FillBounds
    "contain" -> ContentScale.Fit
    "fitWidth" -> ContentScale.FillWidth
    "fitHeight" -> ContentScale.FillHeight
    "none" -> ContentScale.None
    else -> ContentScale.Crop
}

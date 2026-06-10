package com.digia.engage.internal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.digia.engage.internal.model.NudgeBox
import com.digia.engage.internal.model.NudgeSelfAlign

/** Decorator (GoF): wraps any rendered node with its shared NudgeBox. */
@Composable
internal fun NudgeBoxDecorator(box: NudgeBox, content: @Composable () -> Unit) {
    val radius = if (box.borderRadius > 0f) RoundedCornerShape(box.borderRadius.dp) else null
    val hasBg = box.background != null
    val hasBorder = box.borderWidth > 0f && box.borderColor != null
    val hasPadding = box.paddingLeft > 0f || box.paddingTop > 0f || box.paddingRight > 0f || box.paddingBottom > 0f
    val hasMargin = box.marginLeft > 0f || box.marginTop > 0f || box.marginRight > 0f || box.marginBottom > 0f

    var mod: Modifier = Modifier
    if (hasMargin) {
        mod = mod.padding(
            start = box.marginLeft.dp, top = box.marginTop.dp,
            end = box.marginRight.dp, bottom = box.marginBottom.dp,
        )
    }
    if (box.fillWidth) mod = mod.fillMaxWidth()
    box.fixedWidth?.let { mod = mod.width(it.dp) }
    box.fixedHeight?.let { mod = mod.height(it.dp) }
    if (radius != null) mod = mod.clip(radius)
    if (hasBg) mod = mod.background(Color(box.background!!))
    if (hasBorder && radius != null) {
        mod = mod.border(box.borderWidth.dp, Color(box.borderColor!!), radius)
    }
    if (hasPadding) {
        mod = mod.padding(
            start = box.paddingLeft.dp, top = box.paddingTop.dp,
            end = box.paddingRight.dp, bottom = box.paddingBottom.dp,
        )
    }

    val selfAlign = box.selfAlign
    if (selfAlign != null) {
        val wca = when (selfAlign) {
            NudgeSelfAlign.START -> Alignment.Start
            NudgeSelfAlign.CENTER -> Alignment.CenterHorizontally
            NudgeSelfAlign.END -> Alignment.End
        }
        Box(modifier = Modifier.fillMaxWidth().wrapContentWidth(wca)) {
            Box(modifier = mod) { content() }
        }
    } else {
        Box(modifier = mod) { content() }
    }
}

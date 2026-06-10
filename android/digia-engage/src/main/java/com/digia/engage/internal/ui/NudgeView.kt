package com.digia.engage.internal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.digia.engage.internal.model.NudgeColumn

/** Renders the nudge content column — direct port of Flutter NudgeView. */
@Composable
internal fun NudgeView(content: NudgeColumn) {
    val crossAlign = when (content.crossAxisAlignment) {
        "center" -> Alignment.CenterHorizontally
        "end" -> Alignment.End
        else -> Alignment.Start
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = crossAlign,
    ) {
        content.children.forEachIndexed { i, node ->
            if (i > 0 && content.spacing > 0f) {
                Spacer(Modifier.height(content.spacing.dp))
            }
            NudgeNodeRenderer(node)
        }
    }
}

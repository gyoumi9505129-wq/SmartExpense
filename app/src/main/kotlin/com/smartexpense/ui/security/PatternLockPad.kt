package com.smartexpense.ui.security

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary
import kotlin.math.hypot

private const val CELL_COUNT = 9
private const val COLUMNS = 3
const val PATTERN_MIN_LENGTH = 4

@Composable
fun PatternLockPad(
    selected: List<Int>,
    onPatternChange: (List<Int>) -> Unit,
    onPatternComplete: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    lineColor: Color = TextPrimary,
    dotColor: Color = TextSecondary
) {
    var finger by remember { mutableStateOf<Offset?>(null) }
    var cellCenters by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var drawing by remember { mutableStateOf(selected) }

    // 부모에서 selected를 비우면(확인 입력 등) 화면의 이전 패턴도 즉시 지웁니다.
    LaunchedEffect(selected) {
        if (selected.isEmpty() && drawing.isNotEmpty()) {
            drawing = emptyList()
            finger = null
        } else if (selected.isNotEmpty() && drawing != selected) {
            drawing = selected
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { start ->
                        finger = start
                        val first = nearestCell(start, cellCenters)
                        drawing = if (first != null) listOf(first) else emptyList()
                        onPatternChange(drawing)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val pos = change.position
                        finger = pos
                        val cell = nearestCell(pos, cellCenters) ?: return@detectDragGestures
                        if (cell !in drawing) {
                            drawing = drawing + cell
                            onPatternChange(drawing)
                        }
                    },
                    onDragEnd = {
                        finger = null
                        val done = drawing
                        if (done.isNotEmpty()) {
                            onPatternComplete(done)
                        }
                    },
                    onDragCancel = {
                        finger = null
                    }
                )
            }
    ) {
        val padding = 28.dp.toPx()
        val usableW = size.width - padding * 2
        val usableH = size.height - padding * 2
        val stepX = usableW / (COLUMNS - 1)
        val stepY = usableH / (COLUMNS - 1)
        val centers = List(CELL_COUNT) { index ->
            val col = index % COLUMNS
            val row = index / COLUMNS
            Offset(padding + stepX * col, padding + stepY * row)
        }
        cellCenters = centers

        val hitRadius = 28.dp.toPx()
        val lineStroke = 4.dp.toPx()
        val path = if (drawing.isNotEmpty()) drawing else selected

        if (path.size >= 2) {
            for (i in 0 until path.lastIndex) {
                drawLine(
                    color = lineColor,
                    start = centers[path[i]],
                    end = centers[path[i + 1]],
                    strokeWidth = lineStroke,
                    cap = StrokeCap.Round
                )
            }
        }
        val last = path.lastOrNull()
        if (last != null && finger != null) {
            drawLine(
                color = lineColor.copy(alpha = 0.7f),
                start = centers[last],
                end = finger!!,
                strokeWidth = lineStroke,
                cap = StrokeCap.Round
            )
        }

        centers.forEachIndexed { index, center ->
            val selectedDot = index in path
            drawCircle(
                color = if (selectedDot) lineColor else dotColor,
                radius = if (selectedDot) 12.dp.toPx() else 8.dp.toPx(),
                center = center
            )
            drawCircle(
                color = (if (selectedDot) lineColor else dotColor).copy(alpha = 0.25f),
                radius = hitRadius,
                center = center
            )
        }
    }
}

private fun nearestCell(point: Offset, centers: List<Offset>): Int? {
    if (centers.size != CELL_COUNT) return null
    var best = -1
    var bestDist = Float.MAX_VALUE
    centers.forEachIndexed { index, center ->
        val dist = hypot((point.x - center.x).toDouble(), (point.y - center.y).toDouble()).toFloat()
        if (dist < bestDist) {
            bestDist = dist
            best = index
        }
    }
    return best.takeIf { it >= 0 && bestDist <= 56f }
}

package dev.mathieu.youcandoit.settings

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.mathieu.youcandoit.R

/** A color as hue (0–360), saturation and value (0–1): what the picker's square and bar move. */
@Immutable
data class Hsv(val hue: Float, val saturation: Float, val value: Float) {
    fun toColor(): Color = Color.hsv(hue, saturation, value)

    companion object {
        fun of(color: Color): Hsv {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(color.toArgb(), hsv)
            return Hsv(hsv[0], hsv[1], hsv[2])
        }
    }
}

/**
 * Any color: the square picks how vivid and how bright (left to right, top to bottom), the bar below
 * picks the hue. The bar can be set by screen readers too; the quick picks next to it cover the square.
 */
@Composable
fun ColorPicker(hsv: Hsv, onChange: (Hsv) -> Unit, modifier: Modifier = Modifier) {
    val current by rememberUpdatedState(hsv)
    val change by rememberUpdatedState(onChange)
    val hueLabel = stringResource(R.string.picker_hue)
    val shadeLabel = stringResource(R.string.picker_shade)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f)
                .clip(RoundedCornerShape(12.dp))
                .semantics { contentDescription = shadeLabel }
                .trackPosition { position, size ->
                    change(
                        current.copy(
                            saturation = (position.x / size.width).coerceIn(0f, 1f),
                            value = 1f - (position.y / size.height).coerceIn(0f, 1f),
                        ),
                    )
                }
                .drawBehind {
                    drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hsv.hue, 1f, 1f))))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    thumb(Offset(hsv.saturation * size.width, (1f - hsv.value) * size.height))
                },
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .semantics {
                    contentDescription = hueLabel
                    progressBarRangeInfo = ProgressBarRangeInfo(hsv.hue, 0f..360f)
                    setProgress { target ->
                        change(current.copy(hue = target.coerceIn(0f, 360f)))
                        true
                    }
                }
                .trackPosition { position, size -> change(current.copy(hue = (position.x / size.width).coerceIn(0f, 1f) * 360f)) }
                .drawBehind {
                    drawRect(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }))
                    thumb(Offset(hsv.hue / 360f * size.width, size.height / 2))
                },
        )
    }
}

/** Calls [onPosition] where the finger goes down, then as it moves. */
private fun Modifier.trackPosition(onPosition: (Offset, IntSize) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        onPosition(down.position, size)
        down.consume()
        drag(down.id) {
            onPosition(it.position, size)
            it.consume()
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.thumb(center: Offset) {
    drawCircle(Color.Black.copy(alpha = 0.35f), radius = 11.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
    drawCircle(Color.White, radius = 9.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))
}

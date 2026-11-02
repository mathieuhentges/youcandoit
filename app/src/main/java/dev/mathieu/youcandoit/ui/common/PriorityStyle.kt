package dev.mathieu.youcandoit.ui.common

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.mathieu.youcandoit.R
import dev.mathieu.youcandoit.domain.model.Priority

/** [dot] marks the priority (checkbox ring, chip dot); [container]/[content] are the chip colors. */
@Immutable
data class PriorityStyle(val dot: Color, val container: Color, val content: Color)

private val LIGHT = mapOf(
    Priority.URGENT to PriorityStyle(Color(0xFFD93025), Color(0xFFFCE8E6), Color(0xFFA50E0E)),
    Priority.HIGH to PriorityStyle(Color(0xFFF57C00), Color(0xFFFEEFE3), Color(0xFF8F4400)),
    Priority.MEDIUM to PriorityStyle(Color(0xFF00ACC1), Color(0xFFE0F7FA), Color(0xFF006064)),
    Priority.LOW to PriorityStyle(Color(0xFF4285F4), Color(0xFFE8F0FE), Color(0xFF174EA6)),
    Priority.MINIMAL to PriorityStyle(Color(0xFF9AA0A6), Color(0xFFF1F3F4), Color(0xFF45494E)),
)

private val DARK = mapOf(
    Priority.URGENT to PriorityStyle(Color(0xFFF28B82), Color(0xFF5C1A15), Color(0xFFFFDAD6)),
    Priority.HIGH to PriorityStyle(Color(0xFFFFB74D), Color(0xFF552D00), Color(0xFFFFDCC2)),
    Priority.MEDIUM to PriorityStyle(Color(0xFF4DD0E1), Color(0xFF003C44), Color(0xFFB2EBF2)),
    Priority.LOW to PriorityStyle(Color(0xFF8AB4F8), Color(0xFF0B2E66), Color(0xFFD3E3FD)),
    Priority.MINIMAL to PriorityStyle(Color(0xFFBDC1C6), Color(0xFF3A3D41), Color(0xFFE3E5E8)),
)

@Composable
fun Priority.style(): PriorityStyle {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return (if (dark) DARK else LIGHT).getValue(this)
}

@StringRes
fun Priority.labelRes(): Int = when (this) {
    Priority.URGENT -> R.string.priority_urgent
    Priority.HIGH -> R.string.priority_high
    Priority.MEDIUM -> R.string.priority_medium
    Priority.LOW -> R.string.priority_low
    Priority.MINIMAL -> R.string.priority_minimal
}

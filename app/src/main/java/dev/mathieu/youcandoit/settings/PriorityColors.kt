package dev.mathieu.youcandoit.settings

import dev.mathieu.youcandoit.domain.model.Priority

/**
 * The color the user picked for each priority, as ARGB. A priority not in [custom] keeps its
 * original color, which has its own light and dark shades.
 */
data class PriorityColors(val custom: Map<Priority, Int> = emptyMap()) {
    fun customOf(priority: Priority): Int? = custom[priority]

    /** [argb] null puts the original color back. */
    fun with(priority: Priority, argb: Int?): PriorityColors =
        PriorityColors(if (argb == null) custom - priority else custom + (priority to argb))

    val isDefault: Boolean get() = custom.isEmpty()

    companion object {
        val DEFAULT = PriorityColors()
    }
}

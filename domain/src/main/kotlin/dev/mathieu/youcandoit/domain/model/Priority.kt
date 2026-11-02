package dev.mathieu.youcandoit.domain.model

/**
 * Five priority levels. Colors are a presentation concern and are mapped
 * in the UI layer, so the domain stays free of Android types.
 */
enum class Priority(val level: Int) {
    MINIMAL(1),
    LOW(2),
    MEDIUM(3),
    HIGH(4),
    URGENT(5);

    companion object {
        fun fromLevel(level: Int): Priority =
            entries.firstOrNull { it.level == level }
                ?: throw IllegalArgumentException("Priority level must be 1..5, was $level")
    }
}

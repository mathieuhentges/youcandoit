package dev.mathieu.youcandoit.settings

/** Light, dark, or follow the phone. [key] is what's stored: fixed strings, safe from renaming. */
enum class ThemePreference(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    fun isDark(systemIsDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemIsDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromKey(key: String?): ThemePreference = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

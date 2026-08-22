package com.tuneitall.tuner.ui.theme

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

fun resolveDarkTheme(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

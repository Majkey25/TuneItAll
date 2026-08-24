package com.tuneitall.tuner

import java.util.Locale

enum class AppLanguage(val languageTag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    CZECH("cs"),
    GERMAN("de"),
    FRENCH("fr"),
    SPANISH("es"),
}

internal fun appLanguageForTag(languageTag: String?): AppLanguage {
    val language = languageTag?.takeIf(String::isNotBlank)?.let(Locale::forLanguageTag)?.language
        ?: return AppLanguage.SYSTEM
    return AppLanguage.entries.firstOrNull { it.languageTag == language } ?: AppLanguage.SYSTEM
}

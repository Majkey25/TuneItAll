package com.tuneitall.tuner

import kotlin.test.Test
import kotlin.test.assertEquals

class AppLanguageTest {
    @Test
    fun supportedLanguageTagsResolveToPickerOptions() {
        assertEquals(AppLanguage.SYSTEM, appLanguageForTag(null))
        assertEquals(AppLanguage.SYSTEM, appLanguageForTag(""))
        assertEquals(AppLanguage.ENGLISH, appLanguageForTag("en"))
        assertEquals(AppLanguage.CZECH, appLanguageForTag("cs-CZ"))
        assertEquals(AppLanguage.GERMAN, appLanguageForTag("de-DE"))
        assertEquals(AppLanguage.FRENCH, appLanguageForTag("fr-FR"))
        assertEquals(AppLanguage.SPANISH, appLanguageForTag("es-MX"))
        assertEquals(AppLanguage.SYSTEM, appLanguageForTag("it"))
    }

    @Test
    fun pickerContainsOnlyDeclaredLanguages() {
        assertEquals(listOf(null, "en", "cs", "de", "fr", "es"), AppLanguage.entries.map { it.languageTag })
    }
}

package com.tuneitall.tuner

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalizationResourcesTest {
    private val resources = File("src/main/res")

    @Test
    fun everySupportedLocaleDefinesTheCompleteStringSet() {
        val directories = listOf("values", "values-cs", "values-de", "values-fr", "values-es")
        val keysByDirectory = directories.associateWith { directory ->
            val file = File(resources, "$directory/strings.xml")
            assertTrue(file.isFile, "Missing $directory/strings.xml")
            RESOURCE_KEY.findAll(file.readText()).map { it.groupValues[1] }.toSet()
        }

        val baseKeys = keysByDirectory.getValue("values")
        keysByDirectory.forEach { (directory, keys) ->
            assertEquals(baseKeys, keys, "$directory must define every localized string and plural")
        }
    }

    @Test
    fun localeConfigListsEverySelectableLanguage() {
        val file = File(resources, "xml/locales_config.xml")
        assertTrue(file.isFile, "Missing locales_config.xml")
        val tags = LANGUAGE_TAG.findAll(file.readText()).map { it.groupValues[1] }.toSet()

        assertEquals(setOf("en", "cs", "de", "fr", "es"), tags)
    }

    private companion object {
        val RESOURCE_KEY = Regex("""<(?:string|plurals) name="([^"]+)"""")
        val LANGUAGE_TAG = Regex("""android:name="([^"]+)"""")
    }
}

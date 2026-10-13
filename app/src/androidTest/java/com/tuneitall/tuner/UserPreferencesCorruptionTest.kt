package com.tuneitall.tuner

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.model.TuningCatalog
import com.tuneitall.tuner.storage.CustomTuningCodec
import com.tuneitall.tuner.storage.UserPreferences
import com.tuneitall.tuner.ui.TunerViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPreferencesCorruptionTest {
    @Test
    fun wrongTypedLastTuningDoesNotPreventStartup() = assertSafeStartup("last_tuning_id")

    @Test
    fun wrongTypedReferencePitchDoesNotPreventStartup() = assertSafeStartup("a4_hertz")

    @Test
    fun wrongTypedFavoritesDoNotPreventStartup() = assertSafeStartup("favorite_ids")

    @Test
    fun wrongTypedCustomTuningsDoNotPreventStartup() = assertSafeStartup("custom_tunings")

    @Test
    fun staleFavoritesDoNotPreventAddingAValidFavorite() = withSavedPreferences { application, raw, store ->
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val standard = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
        val staleFavorites = (0 until 200).map { "missing-favorite-$it" }.toSet()
        assertTrue(raw.edit()
            .putInt("tuner_defaults_version", 2)
            .putString("last_tuning_id", standard.id)
            .putString("custom_tunings", "[]")
            .putStringSet("favorite_ids", staleFavorites)
            .commit())

        lateinit var viewModel: TunerViewModel
        instrumentation.runOnMainSync {
            viewModel = TunerViewModel(application)
            store.put("stale-favorites", viewModel)
        }
        assertEquals(emptySet<String>(), viewModel.uiState.value.favoriteIds)
        assertEquals(staleFavorites, raw.getStringSet("favorite_ids", emptySet()))
        instrumentation.runOnMainSync { viewModel.toggleFavorite(standard.id) }

        val expected = setOf(standard.id)
        assertEquals(expected, viewModel.uiState.value.favoriteIds)
        assertEquals(expected, raw.getStringSet("favorite_ids", emptySet()))
        assertEquals(expected, UserPreferences(application).favoriteIds)
        assertFalse(viewModel.uiState.value.listening)
        assertFalse(viewModel.uiState.value.referenceTonePlaying)
    }

    private fun assertSafeStartup(corruptedKey: String) = withSavedPreferences { application, raw, store ->
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val standard = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
        val custom = standard.copy(id = "custom-corruption-test", name = "Saved custom", layouts = setOf(standard.layouts.first()))
        val favorites = setOf(standard.id, custom.id)
        assertTrue(raw.edit()
            .putInt("tuner_defaults_version", 2)
            .putString("last_tuning_id", custom.id)
            .putLong("a4_hertz", 444.0.toBits())
            .putStringSet("favorite_ids", favorites)
            .putString("custom_tunings", CustomTuningCodec.encode(listOf(custom)))
            .putBoolean(corruptedKey, true)
            .commit())

        lateinit var viewModel: TunerViewModel
        instrumentation.runOnMainSync {
            viewModel = TunerViewModel(application)
            store.put("corrupt-preferences", viewModel)
        }
        val state = viewModel.uiState.value
        val customMissing = corruptedKey == "custom_tunings"
        assertEquals(if (corruptedKey == "last_tuning_id" || customMissing) standard.id else custom.id, state.tuning.id)
        assertEquals(
            ReferencePitch(if (corruptedKey == "a4_hertz") ReferencePitch.DEFAULT_HERTZ else 444.0),
            state.referencePitch,
        )
        val restoredFavorites = when {
            corruptedKey == "favorite_ids" -> emptySet()
            customMissing -> favorites - custom.id
            else -> favorites
        }
        assertEquals(restoredFavorites, state.favoriteIds)
        assertEquals(if (customMissing) emptyList() else listOf(custom), state.customTunings)
        assertFalse(state.listening)
        assertFalse(state.referenceTonePlaying)
        assertTrue(raw.getBoolean(corruptedKey, false))
        val preferences = UserPreferences(application)
        if (corruptedKey != "favorite_ids") assertEquals(favorites, preferences.favoriteIds)
        if (!customMissing) assertEquals(listOf(custom), preferences.customTunings)
    }

    private fun withSavedPreferences(test: (Application, SharedPreferences, ViewModelStore) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = instrumentation.targetContext.applicationContext as Application
        val raw = application.getSharedPreferences("tuneitall_preferences", Context.MODE_PRIVATE)
        val keys = setOf("last_tuning_id", "a4_hertz", "favorite_ids", "custom_tunings", "tuner_defaults_version")
        val original = raw.all.filterKeys { it in keys }
        val store = ViewModelStore()
        try {
            test(application, raw, store)
        } finally {
            instrumentation.runOnMainSync(store::clear)
            val editor = raw.edit()
            keys.forEach { key ->
                when (val value = original[key]) {
                    null -> editor.remove(key)
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> editor.putStringSet(key, value.map { requireNotNull(it as? String) }.toSet())
                    else -> error("Unsupported saved preference type for $key")
                }
            }
            assertTrue(editor.commit())
        }
    }
}

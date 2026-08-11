package com.tuneitall.tuner.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.tuneitall.tuner.audio.DetectionSensitivity
import com.tuneitall.tuner.model.HeadstockLayout
import com.tuneitall.tuner.model.Instrument
import com.tuneitall.tuner.model.MidiNote
import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.model.TuningPreset
import com.tuneitall.tuner.tuner.TunerMode
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

enum class NoteNotation {
    SHARPS,
    FLATS,
}

class UserPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    var mode: TunerMode
        get() = preferences.enumValue(MODE_KEY, TunerMode.AUTO)
        set(value) = preferences.edit { putString(MODE_KEY, value.name) }

    var lastTuningId: String
        get() = preferences.getString(LAST_TUNING_KEY, DEFAULT_TUNING_ID).orEmpty().ifBlank { DEFAULT_TUNING_ID }
        set(value) {
            require(value.isNotBlank()) { "Last tuning ID must not be blank" }
            preferences.edit { putString(LAST_TUNING_KEY, value) }
        }

    var headstockLayout: HeadstockLayout
        get() = preferences.enumValue(HEADSTOCK_LAYOUT_KEY, HeadstockLayout.SPLIT_3_3)
        set(value) = preferences.edit { putString(HEADSTOCK_LAYOUT_KEY, value.name) }

    var referencePitch: ReferencePitch
        get() {
            if (!preferences.contains(REFERENCE_PITCH_KEY)) return ReferencePitch(ReferencePitch.DEFAULT_HERTZ)
            val hertz = Double.fromBits(preferences.getLong(REFERENCE_PITCH_KEY, 0L))
            return if (hertz.isFinite() && hertz in ReferencePitch.MIN_HERTZ..ReferencePitch.MAX_HERTZ) {
                ReferencePitch(hertz)
            } else {
                ReferencePitch(ReferencePitch.DEFAULT_HERTZ)
            }
        }
        set(value) = preferences.edit { putLong(REFERENCE_PITCH_KEY, value.hertz.toBits()) }

    var notation: NoteNotation
        get() = preferences.enumValue(NOTATION_KEY, NoteNotation.SHARPS)
        set(value) = preferences.edit { putString(NOTATION_KEY, value.name) }

    var sensitivity: DetectionSensitivity
        get() {
            val value = preferences.getInt(SENSITIVITY_KEY, DetectionSensitivity.DEFAULT_VALUE)
            return if (value in DetectionSensitivity.MIN_VALUE..DetectionSensitivity.MAX_VALUE) {
                DetectionSensitivity(value)
            } else {
                DetectionSensitivity.DEFAULT
            }
        }
        set(value) = preferences.edit { putInt(SENSITIVITY_KEY, value.value) }

    var favoriteIds: Set<String>
        get() = preferences.getStringSet(FAVORITES_KEY, emptySet())
            .orEmpty()
            .asSequence()
            .filter(String::isNotBlank)
            .take(MAX_FAVORITES)
            .toSet()
        set(value) {
            require(value.size <= MAX_FAVORITES) { "Favorite count must not exceed $MAX_FAVORITES" }
            require(value.none(String::isBlank)) { "Favorite IDs must not be blank" }
            preferences.edit { putStringSet(FAVORITES_KEY, value.toSet()) }
        }

    var customTunings: List<TuningPreset>
        get() = CustomTuningCodec.decode(preferences.getString(CUSTOM_TUNINGS_KEY, "[]").orEmpty())
        set(value) {
            val encoded = CustomTuningCodec.encode(value)
            preferences.edit { putString(CUSTOM_TUNINGS_KEY, encoded) }
        }

    private inline fun <reified T : Enum<T>> SharedPreferences.enumValue(key: String, default: T): T {
        val stored = getString(key, null) ?: return default
        return enumValues<T>().firstOrNull { it.name == stored } ?: default
    }

    private companion object {
        const val PREFERENCES_NAME = "tuneitall_preferences"
        const val MODE_KEY = "mode"
        const val LAST_TUNING_KEY = "last_tuning_id"
        const val HEADSTOCK_LAYOUT_KEY = "headstock_layout"
        const val REFERENCE_PITCH_KEY = "a4_hertz"
        const val NOTATION_KEY = "notation"
        const val SENSITIVITY_KEY = "detection_sensitivity"
        const val FAVORITES_KEY = "favorite_ids"
        const val CUSTOM_TUNINGS_KEY = "custom_tunings"
        const val DEFAULT_TUNING_ID = "guitar-6-standard"
        const val MAX_FAVORITES = 200
    }
}

object CustomTuningCodec {
    const val MAX_CUSTOM_TUNINGS = 100

    fun encode(presets: List<TuningPreset>): String {
        require(presets.size <= MAX_CUSTOM_TUNINGS) {
            "Custom tuning count must not exceed $MAX_CUSTOM_TUNINGS"
        }
        require(presets.map(TuningPreset::id).distinct().size == presets.size) {
            "Custom tuning IDs must be unique"
        }

        val root = JSONArray()
        presets.forEach { preset ->
            require(preset.layouts.size == 1) { "A custom tuning must use exactly one headstock layout" }
            val noteValues = JSONArray()
            preset.notesLowToHigh.forEach { note -> noteValues.put(note.value) }
            root.put(
                JSONObject()
                    .put("id", preset.id)
                    .put("name", preset.name)
                    .put("instrument", preset.instrument.name)
                    .put("notes", noteValues)
                    .put("layout", preset.layouts.single().name),
            )
        }
        return root.toString()
    }

    fun decode(encoded: String): List<TuningPreset> {
        val root = try {
            JSONArray(encoded)
        } catch (_: JSONException) {
            return emptyList()
        }
        val presets = ArrayList<TuningPreset>(minOf(root.length(), MAX_CUSTOM_TUNINGS))
        val ids = HashSet<String>()
        for (index in 0 until root.length()) {
            if (presets.size == MAX_CUSTOM_TUNINGS) break
            val item = root.optJSONObject(index) ?: continue
            val preset = decodeItem(item) ?: continue
            if (ids.add(preset.id)) presets += preset
        }
        return presets
    }

    private fun decodeItem(item: JSONObject): TuningPreset? = try {
        val notesJson = item.getJSONArray("notes")
        val notes = ArrayList<MidiNote>(notesJson.length())
        for (index in 0 until notesJson.length()) {
            val raw = notesJson.get(index)
            require(raw is Number) { "MIDI note must be a number" }
            val value = raw.toDouble()
            require(value.isFinite() && value % 1.0 == 0.0 && value in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
                "MIDI note must be an integer"
            }
            notes += MidiNote(value.toInt())
        }
        TuningPreset(
            id = item.getString("id"),
            name = item.getString("name"),
            instrument = Instrument.valueOf(item.getString("instrument")),
            notesLowToHigh = notes,
            layouts = setOf(HeadstockLayout.valueOf(item.getString("layout"))),
        )
    } catch (_: JSONException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

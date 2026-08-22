package com.tuneitall.tuner.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.tuneitall.tuner.audio.AudioInputSource
import com.tuneitall.tuner.audio.DetectionSensitivity
import com.tuneitall.tuner.audio.ResponseMode
import com.tuneitall.tuner.audio.TunerAudioSettings
import com.tuneitall.tuner.audio.TunerProfile
import com.tuneitall.tuner.model.HeadstockLayout
import com.tuneitall.tuner.model.Instrument
import com.tuneitall.tuner.model.MidiNote
import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.model.TuningPreset
import com.tuneitall.tuner.metronome.Bpm
import com.tuneitall.tuner.metronome.MetronomeSettings
import com.tuneitall.tuner.metronome.MetronomeSound
import com.tuneitall.tuner.tuner.TunerMode
import com.tuneitall.tuner.ui.theme.ThemeMode
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

    var themeMode: ThemeMode
        get() = preferences.enumValue(THEME_MODE_KEY, ThemeMode.SYSTEM)
        set(value) = preferences.edit { putString(THEME_MODE_KEY, value.name) }

    var sensitivity: DetectionSensitivity
        get() = preferences.detectionSensitivity(SENSITIVITY_KEY, DetectionSensitivity.DEFAULT)
        set(value) = preferences.edit { putInt(SENSITIVITY_KEY, value.value) }

    var tunerAudioSettings: TunerAudioSettings
        get() {
            val defaults = TunerProfile.BALANCED.settings
            return TunerAudioSettings(
                sensitivity = preferences.detectionSensitivity(SENSITIVITY_KEY, defaults.sensitivity),
                response = preferences.enumValue(RESPONSE_MODE_KEY, defaults.response),
                needleStability = preferences.intInRange(NEEDLE_STABILITY_KEY, defaults.needleStability, 0..100),
                noiseRejection = preferences.intInRange(NOISE_REJECTION_KEY, defaults.noiseRejection, 0..100),
                harmonicProtection = preferences.intInRange(
                    HARMONIC_PROTECTION_KEY,
                    defaults.harmonicProtection,
                    0..100,
                ),
                inTuneCents = preferences.intInRange(IN_TUNE_CENTS_KEY, defaults.inTuneCents, 1..10),
                confirmationMillis = preferences.duration(
                    CONFIRMATION_MILLIS_KEY,
                    defaults.confirmationMillis,
                    100L..1_000L,
                ),
                readingHoldMillis = preferences.duration(
                    READING_HOLD_MILLIS_KEY,
                    defaults.readingHoldMillis,
                    0L..1_000L,
                ),
                inputSource = preferences.enumValue(INPUT_SOURCE_KEY, defaults.inputSource),
            )
        }
        set(value) = preferences.edit {
            putInt(SENSITIVITY_KEY, value.sensitivity.value)
            putString(RESPONSE_MODE_KEY, value.response.name)
            putInt(NEEDLE_STABILITY_KEY, value.needleStability)
            putInt(NOISE_REJECTION_KEY, value.noiseRejection)
            putInt(HARMONIC_PROTECTION_KEY, value.harmonicProtection)
            putInt(IN_TUNE_CENTS_KEY, value.inTuneCents)
            putLong(CONFIRMATION_MILLIS_KEY, value.confirmationMillis)
            putLong(READING_HOLD_MILLIS_KEY, value.readingHoldMillis)
            putString(INPUT_SOURCE_KEY, value.inputSource.name)
        }

    var metronomeSettings: MetronomeSettings
        get() {
            val defaults = MetronomeSettings()
            val accentEvery = preferences.intInRange(
                METRONOME_ACCENT_KEY,
                defaults.accentEvery ?: 0,
                0..12,
            ).takeIf { it >= 2 }
            return MetronomeSettings(
                bpm = Bpm(preferences.intInRange(METRONOME_BPM_KEY, defaults.bpm.value, 20..400)),
                numerator = preferences.intInRange(METRONOME_NUMERATOR_KEY, defaults.numerator, 1..12),
                denominator = preferences.intInSet(
                    METRONOME_DENOMINATOR_KEY,
                    defaults.denominator,
                    setOf(2, 4, 8, 16),
                ),
                subdivision = preferences.intInRange(METRONOME_SUBDIVISION_KEY, defaults.subdivision, 1..4),
                accentEvery = accentEvery,
                volume = preferences.intInRange(METRONOME_VOLUME_KEY, defaults.volume, 0..100),
                countIn = preferences.intInSet(METRONOME_COUNT_IN_KEY, defaults.countIn, setOf(0, 1, 2, 4)),
                sound = preferences.enumValue(METRONOME_SOUND_KEY, defaults.sound),
            )
        }
        set(value) = preferences.edit {
            putInt(METRONOME_BPM_KEY, value.bpm.value)
            putInt(METRONOME_NUMERATOR_KEY, value.numerator)
            putInt(METRONOME_DENOMINATOR_KEY, value.denominator)
            putInt(METRONOME_SUBDIVISION_KEY, value.subdivision)
            putInt(METRONOME_ACCENT_KEY, value.accentEvery ?: 0)
            putInt(METRONOME_VOLUME_KEY, value.volume)
            putInt(METRONOME_COUNT_IN_KEY, value.countIn)
            putString(METRONOME_SOUND_KEY, value.sound.name)
        }

    var metronomeMuted: Boolean
        get() = preferences.valueOrDefault(false) { getBoolean(METRONOME_MUTED_KEY, false) }
        set(value) = preferences.edit { putBoolean(METRONOME_MUTED_KEY, value) }

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
        val stored = valueOrDefault<String?>(null) { getString(key, null) } ?: return default
        return enumValues<T>().firstOrNull { it.name == stored } ?: default
    }

    private fun SharedPreferences.detectionSensitivity(key: String, default: DetectionSensitivity): DetectionSensitivity {
        val value = valueOrDefault(default.value) { getInt(key, default.value) }
        return if (value in DetectionSensitivity.MIN_VALUE..DetectionSensitivity.MAX_VALUE) DetectionSensitivity(value) else default
    }

    private fun SharedPreferences.intInRange(key: String, default: Int, range: IntRange): Int {
        val value = valueOrDefault(default) { getInt(key, default) }
        return if (value in range) value else default
    }

    private fun SharedPreferences.intInSet(key: String, default: Int, valid: Set<Int>): Int {
        val value = valueOrDefault(default) { getInt(key, default) }
        return if (value in valid) value else default
    }

    private fun SharedPreferences.duration(key: String, default: Long, range: LongRange): Long {
        val value = valueOrDefault(default) { getLong(key, default) }
        return if (value in range && value % 50L == 0L) value else default
    }

    private inline fun <T> SharedPreferences.valueOrDefault(default: T, read: SharedPreferences.() -> T): T = try {
        read()
    } catch (_: ClassCastException) {
        default
    }

    private companion object {
        const val PREFERENCES_NAME = "tuneitall_preferences"
        const val MODE_KEY = "mode"
        const val LAST_TUNING_KEY = "last_tuning_id"
        const val HEADSTOCK_LAYOUT_KEY = "headstock_layout"
        const val REFERENCE_PITCH_KEY = "a4_hertz"
        const val NOTATION_KEY = "notation"
        const val THEME_MODE_KEY = "theme_mode"
        const val SENSITIVITY_KEY = "detection_sensitivity"
        const val RESPONSE_MODE_KEY = "tuner_response_mode"
        const val NEEDLE_STABILITY_KEY = "tuner_needle_stability"
        const val NOISE_REJECTION_KEY = "tuner_noise_rejection"
        const val HARMONIC_PROTECTION_KEY = "tuner_harmonic_protection"
        const val IN_TUNE_CENTS_KEY = "tuner_in_tune_cents"
        const val CONFIRMATION_MILLIS_KEY = "tuner_confirmation_millis"
        const val READING_HOLD_MILLIS_KEY = "tuner_reading_hold_millis"
        const val INPUT_SOURCE_KEY = "tuner_input_source"
        const val METRONOME_BPM_KEY = "metronome_bpm"
        const val METRONOME_NUMERATOR_KEY = "metronome_numerator"
        const val METRONOME_DENOMINATOR_KEY = "metronome_denominator"
        const val METRONOME_SUBDIVISION_KEY = "metronome_subdivision"
        const val METRONOME_ACCENT_KEY = "metronome_accent_every"
        const val METRONOME_VOLUME_KEY = "metronome_volume"
        const val METRONOME_COUNT_IN_KEY = "metronome_count_in"
        const val METRONOME_SOUND_KEY = "metronome_sound"
        const val METRONOME_MUTED_KEY = "metronome_muted"
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
        val storedLayout = item.getString("layout")
        TuningPreset(
            id = item.getString("id"),
            name = item.getString("name"),
            instrument = Instrument.valueOf(item.getString("instrument")),
            notesLowToHigh = notes,
            layouts = setOf(
                if (storedLayout == LEGACY_INLINE_6) {
                    HeadstockLayout.SPLIT_3_3
                } else {
                    HeadstockLayout.valueOf(storedLayout)
                },
            ),
        )
    } catch (_: JSONException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private const val LEGACY_INLINE_6 = "INLINE_6"
}

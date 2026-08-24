package com.tuneitall.tuner.model

object TuningCatalog {
    val presets: List<TuningPreset> = listOf(
        guitar6("guitar-6-standard", "Standard E", "E2 A2 D3 G3 B3 E4"),
        guitar6("guitar-6-standard-e-flat", "Standard E♭", "Eb2 Ab2 Db3 Gb3 Bb3 Eb4"),
        guitar6("guitar-6-standard-d", "Standard D", "D2 G2 C3 F3 A3 D4"),
        guitar6("guitar-6-standard-c-sharp", "Standard C♯", "C#2 F#2 B2 E3 G#3 C#4"),
        guitar6("guitar-6-standard-c", "Standard C", "C2 F2 Bb2 Eb3 G3 C4"),
        guitar6("guitar-6-standard-b", "Standard B", "B1 E2 A2 D3 F#3 B3"),
        guitar6("guitar-6-standard-b-flat", "Standard B♭", "Bb1 Eb2 Ab2 Db3 F3 Bb3"),
        guitar6("guitar-6-standard-a", "Standard A", "A1 D2 G2 C3 E3 A3"),
        guitar6("guitar-6-drop-d", "Drop D", "D2 A2 D3 G3 B3 E4"),
        guitar6("guitar-6-drop-c-sharp", "Drop C♯", "C#2 G#2 C#3 F#3 A#3 D#4"),
        guitar6("guitar-6-drop-c", "Drop C", "C2 G2 C3 F3 A3 D4"),
        guitar6("guitar-6-drop-b", "Drop B", "B1 F#2 B2 E3 G#3 C#4"),
        guitar6("guitar-6-drop-b-flat", "Drop B♭", "Bb1 F2 Bb2 Eb3 G3 C4"),
        guitar6("guitar-6-drop-a", "Drop A", "A1 E2 A2 D3 F#3 B3"),
        guitar6("guitar-6-drop-a-flat", "Drop A♭", "Ab1 Eb2 Ab2 Db3 F3 Bb3"),
        guitar6("guitar-6-drop-g", "Drop G", "G1 D2 G2 C3 E3 A3"),
        guitar6("guitar-6-drop-g-flat", "Drop G♭", "Gb1 Db2 Gb2 B2 Eb3 Ab3"),
        guitar6("guitar-6-drop-f", "Drop F", "F1 C2 F2 Bb2 D3 G3"),
        guitar6("guitar-6-dadgad", "DADGAD", "D2 A2 D3 G3 A3 D4"),
        guitar6("guitar-6-open-d", "Open D", "D2 A2 D3 F#3 A3 D4"),
        guitar6("guitar-6-open-e", "Open E", "E2 B2 E3 G#3 B3 E4"),
        guitar6("guitar-6-open-g", "Open G", "D2 G2 D3 G3 B3 D4"),
        guitar6("guitar-6-open-a", "Open A", "E2 A2 E3 A3 C#4 E4"),
        guitar7("guitar-7-standard", "Standard B", "B1 E2 A2 D3 G3 B3 E4"),
        guitar7("guitar-7-drop-a", "Drop A", "A1 E2 A2 D3 G3 B3 E4"),
        guitar7("guitar-7-drop-g", "Drop G", "G1 D2 G2 C3 F3 A3 D4"),
        guitar7("guitar-7-drop-f", "Drop F", "F1 C2 F2 Bb2 Eb3 G3 C4"),
        guitar8("guitar-8-standard", "Standard F♯", "F#1 B1 E2 A2 D3 G3 B3 E4"),
        guitar8("guitar-8-drop-e", "Drop E", "E1 B1 E2 A2 D3 G3 B3 E4"),
        guitar9("guitar-9-standard", "Standard C♯", "C#1 F#1 B1 E2 A2 D3 G3 B3 E4"),
        guitar9("guitar-9-drop-b", "Drop B", "B0 F#1 B1 E2 A2 D3 G3 B3 E4"),
        preset(
            id = "bass-4-standard",
            name = "Standard E",
            instrument = Instrument.BASS,
            spec = "E1 A1 D2 G2",
            layouts = BASS_4_LAYOUTS,
        ),
        preset(
            id = "bass-4-drop-d",
            name = "Drop D",
            instrument = Instrument.BASS,
            spec = "D1 A1 D2 G2",
            layouts = BASS_4_LAYOUTS,
        ),
        preset(
            id = "bass-4-standard-d",
            name = "Standard D",
            instrument = Instrument.BASS,
            spec = "D1 G1 C2 F2",
            layouts = BASS_4_LAYOUTS,
        ),
        preset(
            id = "bass-4-bead",
            name = "BEAD",
            instrument = Instrument.BASS,
            spec = "B0 E1 A1 D2",
            layouts = BASS_4_LAYOUTS,
        ),
        preset(
            id = "ukulele-standard",
            name = "Standard C",
            instrument = Instrument.UKULELE,
            spec = "G4 C4 E4 A4",
            layouts = UKULELE_LAYOUTS,
        ),
        preset(
            id = "ukulele-low-g",
            name = "Low G",
            instrument = Instrument.UKULELE,
            spec = "G3 C4 E4 A4",
            layouts = UKULELE_LAYOUTS,
        ),
        preset(
            id = "ukulele-baritone",
            name = "Baritone",
            instrument = Instrument.UKULELE,
            spec = "D3 G3 B3 E4",
            layouts = UKULELE_LAYOUTS,
        ),
    )

    private val presetsById: Map<String, TuningPreset> = presets.associateBy(TuningPreset::id)

    fun byId(id: String): TuningPreset? = presetsById[id]
}

internal fun notes(spec: String): List<MidiNote> {
    require(spec.isNotBlank()) { "Note specification must not be blank" }
    return spec.trim().split(NOTE_SEPARATOR).map(::parseNote)
}

private fun parseNote(token: String): MidiNote {
    val match = NOTE_PATTERN.matchEntire(token)
        ?: throw IllegalArgumentException("Invalid scientific note: $token")
    val letter = match.groupValues[1].single()
    val accidental = match.groupValues[2]
    val octave = match.groupValues[3].toInt()
    val semitone = when (letter) {
        'C' -> 0
        'D' -> 2
        'E' -> 4
        'F' -> 5
        'G' -> 7
        'A' -> 9
        'B' -> 11
        else -> error("Regex accepted an unsupported note")
    } + when (accidental) {
        "#" -> 1
        "b" -> -1
        else -> 0
    }
    return MidiNote((octave + 1) * 12 + semitone)
}

private fun guitar6(id: String, name: String, spec: String): TuningPreset =
    preset(id, name, Instrument.GUITAR, spec, GUITAR_6_LAYOUTS)

private fun guitar7(id: String, name: String, spec: String): TuningPreset =
    preset(id, name, Instrument.GUITAR, spec, GUITAR_7_LAYOUTS)

private fun guitar8(id: String, name: String, spec: String): TuningPreset =
    preset(id, name, Instrument.GUITAR, spec, GUITAR_8_LAYOUTS)

private fun guitar9(id: String, name: String, spec: String): TuningPreset =
    preset(id, name, Instrument.GUITAR, spec, GUITAR_9_LAYOUTS)

private fun preset(
    id: String,
    name: String,
    instrument: Instrument,
    spec: String,
    layouts: Set<HeadstockLayout>,
): TuningPreset = TuningPreset(id, name, instrument, notes(spec), layouts)

private val NOTE_PATTERN = Regex("^([A-G])([#b]?)(-1|[0-9])$")
private val NOTE_SEPARATOR = Regex("\\s+")
private val GUITAR_6_LAYOUTS = setOf(HeadstockLayout.SPLIT_3_3, HeadstockLayout.INLINE_6)
private val GUITAR_7_LAYOUTS = setOf(HeadstockLayout.INLINE_7, HeadstockLayout.SPLIT_4_3)
private val GUITAR_8_LAYOUTS = setOf(HeadstockLayout.INLINE_8, HeadstockLayout.SPLIT_4_4)
private val GUITAR_9_LAYOUTS = setOf(HeadstockLayout.INLINE_9, HeadstockLayout.SPLIT_5_4)
private val BASS_4_LAYOUTS = setOf(HeadstockLayout.INLINE_4, HeadstockLayout.SPLIT_2_2)
private val UKULELE_LAYOUTS = setOf(HeadstockLayout.SPLIT_2_2)

package com.tuneitall.tuner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tuneitall.tuner.R
import com.tuneitall.tuner.model.TuningPreset
import com.tuneitall.tuner.music.Chord
import com.tuneitall.tuner.music.ChordQuality
import com.tuneitall.tuner.music.ChordVoicing
import com.tuneitall.tuner.music.findPlayableVoicing
import com.tuneitall.tuner.storage.NoteNotation
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TuningSelector(
    tunings: List<TuningPreset>,
    selectedId: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(tunings.isNotEmpty())
    var expanded by remember { mutableStateOf(false) }
    val selected = tunings.firstOrNull { it.id == selectedId } ?: tunings.first()
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("chord_tuning_selector"),
        ) {
            Text("${instrumentName(selected.instrument)} · ${selected.name}")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 420.dp),
        ) {
            tunings.forEach { tuning ->
                DropdownMenuItem(
                    text = { Text("${instrumentName(tuning.instrument)} · ${tuning.name}") },
                    onClick = {
                        onSelected(tuning.id)
                        expanded = false
                    },
                    modifier = Modifier.testTag("chord_tuning_${tuning.id}"),
                )
            }
        }
    }
}

@Composable
fun ChordDiagram(
    chord: Chord,
    tuning: TuningPreset,
    notation: NoteNotation,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
) {
    val result by produceState(VoicingResult(ready = false, voicing = null), chord, tuning) {
        value = withContext(Dispatchers.Default) {
            VoicingResult(ready = true, voicing = findPlayableVoicing(tuning.notesLowToHigh, chord))
        }
    }
    if (!result.ready) {
        Box(modifier.fillMaxWidth().height(220.dp))
        return
    }
    val voicing = result.voicing
    if (voicing == null) {
        Text(stringResource(R.string.no_chord_voicing), modifier = modifier)
        return
    }
    val fretted = voicing.frets.filter { it > 0 }
    val baseFret = if ((fretted.maxOrNull() ?: 0) <= DISPLAY_FRETS) 1 else requireNotNull(fretted.minOrNull())
    val description = stringResource(
        R.string.chord_diagram_description,
        voicing.frets.joinToString { if (it < 0) "x" else it.toString() },
    )
    val lineColor = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary

    Column(modifier) {
        if (baseFret > 1) {
            Text(
                stringResource(R.string.fret_number, baseFret),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .semantics { contentDescription = description }
                .testTag("chord_diagram"),
        ) {
            val side = 28.dp.toPx()
            val top = 34.dp.toPx()
            val bottom = size.height - 12.dp.toPx()
            val stringGap = (size.width - side * 2f) / (voicing.frets.size - 1)
            val fretGap = (bottom - top) / DISPLAY_FRETS
            repeat(voicing.frets.size) { string ->
                val x = side + string * stringGap
                drawLine(lineColor, Offset(x, top), Offset(x, bottom), 1.dp.toPx())
            }
            repeat(DISPLAY_FRETS + 1) { fret ->
                val y = top + fret * fretGap
                drawLine(
                    lineColor,
                    Offset(side, y),
                    Offset(size.width - side, y),
                    if (fret == 0 && baseFret == 1) 4.dp.toPx() else 1.dp.toPx(),
                    StrokeCap.Round,
                )
            }
            voicing.frets.forEachIndexed { string, fret ->
                val x = side + string * stringGap
                when {
                    fret < 0 -> {
                        val radius = 5.dp.toPx()
                        val y = 16.dp.toPx()
                        drawLine(lineColor, Offset(x - radius, y - radius), Offset(x + radius, y + radius), 2.dp.toPx())
                        drawLine(lineColor, Offset(x + radius, y - radius), Offset(x - radius, y + radius), 2.dp.toPx())
                    }

                    fret == 0 -> drawCircle(
                        lineColor,
                        radius = 5.dp.toPx(),
                        center = Offset(x, 16.dp.toPx()),
                        style = Stroke(2.dp.toPx()),
                    )

                    else -> {
                        val row = fret - baseFret
                        val y = top + (row + 0.5f) * fretGap
                        drawCircle(accent, radius = 8.dp.toPx(), center = Offset(x, y))
                        drawCircle(lineColor, radius = 8.dp.toPx(), center = Offset(x, y), style = Stroke(1.dp.toPx()))
                    }
                }
            }
        }
        if (showLabel) {
            Text(
                text = formatChord(chord, notation),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().testTag("chord_diagram_label"),
            )
        }
    }
}

@Composable
fun chordQualityName(quality: ChordQuality): String = stringResource(
    when (quality) {
        ChordQuality.MAJOR -> R.string.chord_major
        ChordQuality.MINOR -> R.string.chord_minor
        ChordQuality.DOMINANT_SEVENTH -> R.string.chord_dominant_seventh
    },
)

fun formatChord(chord: Chord, notation: NoteNotation): String {
    val names = if (notation == NoteNotation.FLATS) FLAT_NAMES else SHARP_NAMES
    val suffix = when (chord.quality) {
        ChordQuality.MAJOR -> ""
        ChordQuality.MINOR -> "m"
        ChordQuality.DOMINANT_SEVENTH -> "7"
    }
    return names[chord.rootPitchClass] + suffix
}

fun formatSongTime(millis: Long): String {
    require(millis >= 0L)
    val totalSeconds = millis / 1_000L
    return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60L, totalSeconds % 60L)
}

private val SHARP_NAMES = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
private val FLAT_NAMES = listOf("C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B")
private data class VoicingResult(val ready: Boolean, val voicing: ChordVoicing?)
private const val DISPLAY_FRETS = 5

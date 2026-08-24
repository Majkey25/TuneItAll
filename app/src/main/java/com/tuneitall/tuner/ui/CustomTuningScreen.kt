package com.tuneitall.tuner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tuneitall.tuner.R
import com.tuneitall.tuner.model.HeadstockLayout
import com.tuneitall.tuner.model.Instrument
import com.tuneitall.tuner.model.MidiNote
import com.tuneitall.tuner.model.TuningPreset
import com.tuneitall.tuner.model.notes
import com.tuneitall.tuner.storage.CustomTuningCodec
import java.util.Locale
import java.util.UUID

@Composable
fun CustomTuningScreen(
    existing: List<TuningPreset>,
    onSave: (TuningPreset) -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var instrument by remember { mutableStateOf(Instrument.GUITAR) }
    var stringCount by remember { mutableIntStateOf(6) }
    var noteInputs by remember { mutableStateOf(defaultNotes(instrument, stringCount)) }
    var layout by remember { mutableStateOf(layoutsFor(instrument, stringCount).first()) }
    val parsedNotes = noteInputs.map(::parseSingleNote)
    val duplicateName = existing.any { it.name.equals(name.trim(), ignoreCase = true) }
    val valid = name.isNotBlank() && !duplicateName && parsedNotes.all { it != null } &&
        existing.size < CustomTuningCodec.MAX_CUSTOM_TUNINGS

    fun updateShape(nextInstrument: Instrument, nextCount: Int) {
        instrument = nextInstrument
        stringCount = nextCount
        noteInputs = defaultNotes(nextInstrument, nextCount)
        layout = layoutsFor(nextInstrument, nextCount).first()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SecondaryHeader(stringResource(R.string.custom_tuning), onBack)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(MAX_NAME_LENGTH) },
            label = { Text(stringResource(R.string.tuning_name)) },
            isError = duplicateName,
            supportingText = {
                if (duplicateName) Text(stringResource(R.string.duplicate_tuning_name))
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.instrument), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(Instrument.GUITAR, Instrument.BASS, Instrument.UKULELE).forEach { option ->
                FilterChip(
                    selected = instrument == option,
                    onClick = {
                        updateShape(option, if (option == Instrument.GUITAR) 6 else 4)
                    },
                    label = { Text(instrumentName(option)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (instrument == Instrument.GUITAR) {
            Text(stringResource(R.string.strings), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(6, 7, 8, 9).forEach { count ->
                    FilterChip(
                        selected = stringCount == count,
                        onClick = { updateShape(instrument, count) },
                        label = { Text(count.toString()) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        val layoutOptions = layoutsFor(instrument, stringCount)
        if (layoutOptions.size > 1) {
            Text(stringResource(R.string.layout), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                layoutOptions.forEach { option ->
                    FilterChip(
                        selected = layout == option,
                        onClick = { layout = option },
                        label = { Text(layoutName(option)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Text(stringResource(R.string.notes_with_octaves), style = MaterialTheme.typography.titleMedium)
        noteInputs.forEachIndexed { index, value ->
            OutlinedTextField(
                value = value,
                onValueChange = { next ->
                    noteInputs = noteInputs.toMutableList().also { it[index] = next.take(MAX_NOTE_LENGTH) }
                },
                label = { Text(stringResource(R.string.string_note, instrumentStringNumber(index, noteInputs.size))) },
                isError = value.isNotBlank() && parsedNotes[index] == null,
                supportingText = {
                    if (value.isNotBlank() && parsedNotes[index] == null) {
                        Text(stringResource(R.string.note_example))
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (existing.size >= CustomTuningCodec.MAX_CUSTOM_TUNINGS) {
            Text(stringResource(R.string.custom_tuning_limit), color = MaterialTheme.colorScheme.error)
        }
        Button(
            enabled = valid,
            onClick = {
                onSave(
                    TuningPreset(
                        id = "custom-${UUID.randomUUID()}",
                        name = name.trim(),
                        instrument = instrument,
                        notesLowToHigh = parsedNotes.map(::requireNotNull),
                        layouts = setOf(layout),
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.save_and_tune))
        }
    }
}

private fun parseSingleNote(value: String): MidiNote? = try {
    notes(value.trim().replace('♯', '#').replace('♭', 'b').replaceFirstChar { it.uppercase(Locale.ROOT) }).single()
} catch (_: IllegalArgumentException) {
    null
} catch (_: NoSuchElementException) {
    null
}

private fun layoutsFor(instrument: Instrument, stringCount: Int): List<HeadstockLayout> = when (instrument) {
    Instrument.GUITAR -> when (stringCount) {
        6 -> listOf(HeadstockLayout.SPLIT_3_3, HeadstockLayout.INLINE_6)
        7 -> listOf(HeadstockLayout.INLINE_7, HeadstockLayout.SPLIT_4_3)
        8 -> listOf(HeadstockLayout.INLINE_8, HeadstockLayout.SPLIT_4_4)
        9 -> listOf(HeadstockLayout.INLINE_9, HeadstockLayout.SPLIT_5_4)
        else -> error("Unsupported guitar string count")
    }

    Instrument.BASS -> listOf(HeadstockLayout.INLINE_4, HeadstockLayout.SPLIT_2_2)
    Instrument.UKULELE -> listOf(HeadstockLayout.SPLIT_2_2)
    Instrument.CHROMATIC -> error("Chromatic mode does not use custom tunings")
}

private fun defaultNotes(instrument: Instrument, stringCount: Int): List<String> = when (instrument) {
    Instrument.GUITAR -> when (stringCount) {
        6 -> listOf("E2", "A2", "D3", "G3", "B3", "E4")
        7 -> listOf("B1", "E2", "A2", "D3", "G3", "B3", "E4")
        8 -> listOf("F#1", "B1", "E2", "A2", "D3", "G3", "B3", "E4")
        9 -> listOf("C#1", "F#1", "B1", "E2", "A2", "D3", "G3", "B3", "E4")
        else -> error("Unsupported guitar string count")
    }

    Instrument.BASS -> listOf("E1", "A1", "D2", "G2")
    Instrument.UKULELE -> listOf("G4", "C4", "E4", "A4")
    Instrument.CHROMATIC -> error("Chromatic mode does not use custom tunings")
}

private const val MAX_NAME_LENGTH = 60
private const val MAX_NOTE_LENGTH = 4

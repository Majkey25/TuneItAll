package com.tuneitall.tuner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tuneitall.tuner.R
import com.tuneitall.tuner.model.Instrument
import com.tuneitall.tuner.model.TuningPreset
import com.tuneitall.tuner.storage.NoteNotation
import java.util.Locale

@Composable
fun TuningLibraryScreen(
    presets: List<TuningPreset>,
    favoriteIds: Set<String>,
    notation: NoteNotation,
    onSelect: (TuningPreset) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onCreateCustom: () -> Unit,
    onBack: () -> Unit,
    customIds: Set<String> = emptySet(),
    onDeleteCustom: (String) -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var instrument by remember { mutableStateOf<Instrument?>(null) }
    var stringCount by remember { mutableStateOf<Int?>(null) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var pendingDeletion by remember { mutableStateOf<TuningPreset?>(null) }
    val textButtonColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
    val filtered = presets.filter { preset ->
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        (normalizedQuery.isEmpty() || preset.name.lowercase(Locale.ROOT).contains(normalizedQuery)) &&
            (instrument == null || preset.instrument == instrument) &&
            (stringCount == null || preset.notesLowToHigh.size == stringCount) &&
            (!favoritesOnly || preset.id in favoriteIds)
    }.sortedByDescending { preset -> preset.id in favoriteIds }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SecondaryHeader(stringResource(R.string.tuning_library), onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.search_tunings)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = favoritesOnly,
                    onClick = {
                        favoritesOnly = !favoritesOnly
                        if (favoritesOnly) instrument = null
                    },
                    label = { Text(stringResource(R.string.favorites)) },
                    modifier = Modifier.testTag("tuning_filter_favorites"),
                )
            }
            item {
                FilterChip(
                    selected = instrument == null && !favoritesOnly,
                    onClick = {
                        instrument = null
                        favoritesOnly = false
                    },
                    label = { Text(stringResource(R.string.all_instruments)) },
                    modifier = Modifier.testTag("tuning_filter_all"),
                )
            }
            items(Instrument.entries.filterNot { it == Instrument.CHROMATIC }) { option ->
                FilterChip(
                    selected = instrument == option && !favoritesOnly,
                    onClick = {
                        instrument = option
                        favoritesOnly = false
                    },
                    label = { Text(instrumentName(option)) },
                    modifier = Modifier.testTag("tuning_filter_${option.name.lowercase()}"),
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = stringCount == null,
                    onClick = { stringCount = null },
                    label = { Text(stringResource(R.string.all_strings)) },
                )
            }
            items(listOf(4, 5, 6, 7, 8, 9)) { count ->
                FilterChip(
                    selected = stringCount == count,
                    onClick = { stringCount = count },
                    label = { Text(pluralStringResource(R.plurals.string_count, count, count)) },
                )
            }
        }
        TextButton(
            onClick = onCreateCustom,
            colors = textButtonColors,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.create_custom_tuning))
        }
        if (filtered.isEmpty()) {
            Text(
                text = stringResource(R.string.no_tunings_found),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filtered, key = TuningPreset::id) { preset ->
                    ListItem(
                        headlineContent = { Text(preset.name) },
                        supportingContent = {
                            Text(
                                "${instrumentName(preset.instrument)} · " +
                                    preset.notesLowToHigh.joinToString("  ") { formatNote(it, notation) },
                            )
                        },
                        trailingContent = {
                            val favoriteDescription = stringResource(
                                if (preset.id in favoriteIds) {
                                    R.string.remove_favorite
                                } else {
                                    R.string.add_favorite
                                },
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { onToggleFavorite(preset.id) },
                                    colors = textButtonColors,
                                    modifier = Modifier.semantics {
                                        contentDescription = "$favoriteDescription: ${preset.name}"
                                    },
                                ) {
                                    Text(if (preset.id in favoriteIds) "★" else "☆")
                                }
                                if (preset.id in customIds) {
                                    TextButton(
                                        onClick = { pendingDeletion = preset },
                                        colors = textButtonColors,
                                        modifier = Modifier.testTag("delete_tuning_${preset.id}"),
                                    ) {
                                        Text(stringResource(R.string.delete))
                                    }
                                }
                            }
                        },
                        modifier = Modifier.clickable { onSelect(preset) }.testTag("tuning_item_${preset.id}"),
                    )
                }
            }
        }
    }
    pendingDeletion?.let { preset ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text(stringResource(R.string.delete_custom_tuning_title)) },
            text = { Text(stringResource(R.string.delete_custom_tuning_message, preset.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCustom(preset.id)
                        pendingDeletion = null
                    },
                    modifier = Modifier.testTag("confirm_delete_tuning"),
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
fun SecondaryHeader(title: String, onBack: () -> Unit) {
    val contentColor = secondaryHeaderContentColor()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp).testTag("back_button"),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.back),
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
internal fun secondaryHeaderContentColor() = MaterialTheme.colorScheme.onBackground

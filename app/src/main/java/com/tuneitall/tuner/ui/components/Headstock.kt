package com.tuneitall.tuner.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tuneitall.tuner.R
import com.tuneitall.tuner.model.HeadstockLayout
import com.tuneitall.tuner.model.MidiNote
import com.tuneitall.tuner.model.stringCount
import com.tuneitall.tuner.storage.NoteNotation
import com.tuneitall.tuner.ui.formatNote
import com.tuneitall.tuner.ui.instrumentStringNumber
import kotlin.math.max

@Composable
fun Headstock(
    layout: HeadstockLayout,
    notes: List<MidiNote>,
    selectedIndex: Int?,
    confirmed: Boolean,
    notation: NoteNotation,
    onStringSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(notes.size == layout.stringCount) { "Headstock notes must match the layout" }
    require(selectedIndex == null || selectedIndex in notes.indices) { "Selected string must fit the headstock" }
    val sides = layout.sides()
    val rows = max(sides.left, sides.right)
    val inline = sides.left == 0 || sides.right == 0
    val rowHeight = if (inline) INLINE_PEG_ROW_HEIGHT else PEG_ROW_HEIGHT
    val pegSize = if (inline) INLINE_PEG_SIZE else PEG_SIZE
    val totalHeight = HEADSTOCK_TOP_PADDING + HEADSTOCK_BOTTOM_PADDING + rowHeight * rows
    val bodyColor = MaterialTheme.colorScheme.surface
    val outlineColor = MaterialTheme.colorScheme.outline
    val stringColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier.height(totalHeight),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val top = 4.dp.toPx()
            val bottom = size.height - 4.dp.toPx()
            val bodyHalfWidth = HEADSTOCK_BODY_HALF_WIDTH.toPx()
            val narrowHalfWidth = HEADSTOCK_NARROW_HALF_WIDTH.toPx()
            val body = Path().apply {
                moveTo(centerX - narrowHalfWidth, bottom)
                lineTo(centerX - bodyHalfWidth, top + 18.dp.toPx())
                quadraticTo(centerX - bodyHalfWidth, top, centerX - 20.dp.toPx(), top)
                quadraticTo(centerX, top + 12.dp.toPx(), centerX + 20.dp.toPx(), top)
                quadraticTo(centerX + bodyHalfWidth, top, centerX + bodyHalfWidth, top + 18.dp.toPx())
                lineTo(centerX + narrowHalfWidth, bottom)
                close()
            }
            drawPath(body, bodyColor)
            drawPath(body, outlineColor, style = Stroke(width = 1.dp.toPx()))

            repeat(notes.size) { index ->
                val fraction = if (notes.size == 1) 0.5f else index.toFloat() / (notes.size - 1)
                val topX = centerX - 13.dp.toPx() + 26.dp.toPx() * fraction
                val bottomX = centerX - 9.dp.toPx() + 18.dp.toPx() * fraction
                drawLine(
                    color = stringColor.copy(alpha = 0.65f),
                    start = Offset(topX, top + 10.dp.toPx()),
                    end = Offset(bottomX, bottom),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            drawLine(
                color = outlineColor,
                start = Offset(centerX - narrowHalfWidth, bottom - 10.dp.toPx()),
                end = Offset(centerX + narrowHalfWidth, bottom - 10.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
            )

            repeat(rows) { row ->
                val y = HEADSTOCK_TOP_PADDING.toPx() + rowHeight.toPx() * (row + 0.5f)
                if (row < sides.left) {
                    drawLine(
                        color = outlineColor,
                        start = Offset(centerX - bodyHalfWidth, y),
                        end = Offset(centerX - PEG_STEM_END_OFFSET.toPx(), y),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
                if (row < sides.right) {
                    drawLine(
                        color = outlineColor,
                        start = Offset(centerX + bodyHalfWidth, y),
                        end = Offset(centerX + PEG_STEM_END_OFFSET.toPx(), y),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }
        }

        Column(
            verticalArrangement = Arrangement.Top,
            modifier = Modifier.fillMaxSize(),
        ) {
            Spacer(modifier = Modifier.height(HEADSTOCK_TOP_PADDING))
            repeat(rows) { row ->
                val leftIndex = (sides.left - 1 - row).takeIf { row < sides.left }
                val rightIndex = when {
                    row >= sides.right -> null
                    sides.left == 0 -> notes.lastIndex - row
                    else -> sides.left + row
                }
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight),
                ) {
                    Peg(
                        index = leftIndex,
                        notes = notes,
                        selectedIndex = selectedIndex,
                        confirmed = confirmed,
                        notation = notation,
                        onStringSelected = onStringSelected,
                        pegSize = pegSize,
                        compact = inline,
                    )
                    Spacer(modifier = Modifier.width(HEADSTOCK_CENTER_WIDTH))
                    Peg(
                        index = rightIndex,
                        notes = notes,
                        selectedIndex = selectedIndex,
                        confirmed = confirmed,
                        notation = notation,
                        onStringSelected = onStringSelected,
                        pegSize = pegSize,
                        compact = inline,
                    )
                }
            }
        }
    }
}

@Composable
private fun Peg(
    index: Int?,
    notes: List<MidiNote>,
    selectedIndex: Int?,
    confirmed: Boolean,
    notation: NoteNotation,
    onStringSelected: (Int) -> Unit,
    pegSize: Dp,
    compact: Boolean,
) {
    if (index == null) {
        Spacer(modifier = Modifier.size(pegSize))
        return
    }
    val selected = index == selectedIndex
    val note = formatNote(notes[index], notation)
    val stringNumber = instrumentStringNumber(index, notes.size)
    val description = stringResource(R.string.string_button_description, stringNumber, note)
    val colors = if (selected && confirmed) {
        ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
    } else {
        ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    }
    val borderColor = when {
        selected && confirmed -> MaterialTheme.colorScheme.tertiary
        selected -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    OutlinedButton(
        onClick = { onStringSelected(index) },
        shape = CircleShape,
        colors = colors,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .size(pegSize)
            .semantics {
                this.selected = selected
                contentDescription = description
            },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                note,
                fontWeight = FontWeight.SemiBold,
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
            )
            Text("$stringNumber", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private data class HeadstockSides(val left: Int, val right: Int)

private fun HeadstockLayout.sides(): HeadstockSides = when (this) {
    HeadstockLayout.INLINE_4 -> HeadstockSides(4, 0)
    HeadstockLayout.SPLIT_2_2 -> HeadstockSides(2, 2)
    HeadstockLayout.INLINE_6 -> HeadstockSides(6, 0)
    HeadstockLayout.SPLIT_3_3 -> HeadstockSides(3, 3)
    HeadstockLayout.INLINE_7 -> HeadstockSides(7, 0)
    HeadstockLayout.SPLIT_4_3 -> HeadstockSides(4, 3)
    HeadstockLayout.INLINE_8 -> HeadstockSides(8, 0)
    HeadstockLayout.SPLIT_4_4 -> HeadstockSides(4, 4)
    HeadstockLayout.INLINE_9 -> HeadstockSides(9, 0)
    HeadstockLayout.SPLIT_5_4 -> HeadstockSides(5, 4)
}

private val HEADSTOCK_TOP_PADDING = 18.dp
private val HEADSTOCK_BOTTOM_PADDING = 14.dp
private val PEG_ROW_HEIGHT = 58.dp
private val PEG_SIZE = 54.dp
private val INLINE_PEG_ROW_HEIGHT = 44.dp
private val INLINE_PEG_SIZE = 42.dp
private val HEADSTOCK_CENTER_WIDTH = 112.dp
private val HEADSTOCK_BODY_HALF_WIDTH = 42.dp
private val HEADSTOCK_NARROW_HALF_WIDTH = 29.dp
private val PEG_STEM_END_OFFSET = HEADSTOCK_CENTER_WIDTH / 2

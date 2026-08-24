package com.tuneitall.tuner.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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
    val pegWidth = if (inline) INLINE_PEG_WIDTH else PEG_WIDTH
    val pegHeight = if (inline) INLINE_PEG_HEIGHT else PEG_HEIGHT
    val topPadding = if (layout == HeadstockLayout.INLINE_6) 0.dp else HEADSTOCK_TOP_PADDING
    val bottomPadding = if (layout == HeadstockLayout.INLINE_6) 0.dp else HEADSTOCK_BOTTOM_PADDING
    val centerWidth = when (layout) {
        HeadstockLayout.SPLIT_3_3 -> SplitHeadstockGeometry.centerGap
        HeadstockLayout.INLINE_6 -> InlineSixGeometry.centerGap
        else -> HEADSTOCK_CENTER_WIDTH
    }
    val totalHeight = topPadding + bottomPadding + rowHeight * rows
    val bodyColor = MaterialTheme.colorScheme.surface
    val outlineColor = MaterialTheme.colorScheme.outline
    val stringColor = MaterialTheme.colorScheme.onSurfaceVariant

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .height(totalHeight)
                .testTag("headstock"),
            contentAlignment = Alignment.Center,
        ) {
            if (layout == HeadstockLayout.SPLIT_3_3) {
                Image(
                    painter = painterResource(R.drawable.headstock_3x3_noun),
                    contentDescription = null,
                    modifier = Modifier
                        .size(SplitHeadstockGeometry.imageSize)
                        .testTag("headstock_3x3_image"),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                )
            }
            if (layout == HeadstockLayout.INLINE_6) {
                Image(
                    painter = painterResource(R.drawable.headstock_6_inline_noun),
                    contentDescription = null,
                    modifier = Modifier
                        .size(InlineSixGeometry.imageSize)
                        .testTag("headstock_6_inline_image"),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                )
            }
            Canvas(modifier = Modifier.fillMaxSize().testTag("headstock_drawing")) {
                val centerX = size.width / 2f
                val top = 4.dp.toPx()
                val bottom = size.height - 4.dp.toPx()
                val bodyHalfWidth = HEADSTOCK_BODY_HALF_WIDTH.toPx()
                val narrowHalfWidth = HEADSTOCK_NARROW_HALF_WIDTH.toPx()
                if (layout != HeadstockLayout.SPLIT_3_3 && layout != HeadstockLayout.INLINE_6) {
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
                            stringColor.copy(alpha = 0.65f),
                            Offset(topX, top + 10.dp.toPx()),
                            Offset(bottomX, bottom),
                            1.dp.toPx(),
                        )
                    }
                    drawLine(
                        outlineColor,
                        Offset(centerX - narrowHalfWidth, bottom - 10.dp.toPx()),
                        Offset(centerX + narrowHalfWidth, bottom - 10.dp.toPx()),
                        2.dp.toPx(),
                    )
                    repeat(rows) { row ->
                        val y = HEADSTOCK_TOP_PADDING.toPx() + rowHeight.toPx() * (row + 0.5f)
                        if (row < sides.left) {
                            drawLine(
                                outlineColor,
                                Offset(centerX - bodyHalfWidth, y),
                                Offset(centerX - PEG_STEM_END_OFFSET.toPx(), y),
                                2.dp.toPx(),
                            )
                        }
                        if (row < sides.right) {
                            drawLine(
                                outlineColor,
                                Offset(centerX + bodyHalfWidth, y),
                                Offset(centerX + PEG_STEM_END_OFFSET.toPx(), y),
                                2.dp.toPx(),
                            )
                        }
                    }
                }
            }

            Column(
                verticalArrangement = Arrangement.Top,
                modifier = Modifier.fillMaxSize(),
            ) {
                Spacer(modifier = Modifier.height(topPadding))
                repeat(rows) { row ->
                    val (leftIndex, rightIndex) = layout.stringIndicesAtRow(row)
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
                            pegWidth = pegWidth,
                            pegHeight = pegHeight,
                            compact = inline,
                        )
                        Spacer(modifier = Modifier.width(centerWidth))
                        Peg(
                            index = rightIndex,
                            notes = notes,
                            selectedIndex = selectedIndex,
                            confirmed = confirmed,
                            notation = notation,
                            onStringSelected = onStringSelected,
                            pegWidth = pegWidth,
                            pegHeight = pegHeight,
                            compact = inline,
                        )
                    }
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
    pegWidth: Dp,
    pegHeight: Dp,
    compact: Boolean,
) {
    if (index == null) {
        Spacer(modifier = Modifier.width(pegWidth).height(pegHeight))
        return
    }
    val selected = index == selectedIndex
    val note = formatNote(notes[index], notation)
    val stringNumber = instrumentStringNumber(index, notes.size)
    val description = stringResource(R.string.string_button_description, stringNumber, note)
    val colors = if (selected) {
        ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
    } else {
        ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    }
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val borderWidth = when {
        selected && confirmed -> 3.dp
        selected -> 2.dp
        else -> 1.dp
    }

    OutlinedButton(
        onClick = { onStringSelected(index) },
        shape = RoundedCornerShape(8.dp),
        colors = colors,
        border = BorderStroke(borderWidth, borderColor),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .width(pegWidth)
            .height(pegHeight)
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
                modifier = Modifier.testTag("headstock_note_$stringNumber"),
            )
            Text(
                "$stringNumber",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.testTag("headstock_number_$stringNumber"),
            )
        }
    }
}

private data class HeadstockSides(val left: Int, val right: Int)

internal object SplitHeadstockGeometry {
    val imageSize = 196.dp
    val centerGap = 196.dp
}

internal object InlineSixGeometry {
    val imageSize = 244.dp
    val centerGap = 244.dp
}

internal fun HeadstockLayout.stringIndicesAtRow(row: Int): Pair<Int?, Int?> {
    val sides = sides()
    require(row in 0 until max(sides.left, sides.right)) { "Row must fit the headstock" }
    val left = (sides.left - 1 - row).takeIf { row < sides.left }
    val right = when {
        row >= sides.right -> null
        sides.left == 0 -> stringCount - 1 - row
        else -> sides.left + row
    }
    return left to right
}

private fun HeadstockLayout.sides(): HeadstockSides = when (this) {
    HeadstockLayout.INLINE_4 -> HeadstockSides(4, 0)
    HeadstockLayout.SPLIT_2_2 -> HeadstockSides(2, 2)
    HeadstockLayout.SPLIT_3_3 -> HeadstockSides(3, 3)
    HeadstockLayout.INLINE_6 -> HeadstockSides(6, 0)
    HeadstockLayout.INLINE_7 -> HeadstockSides(7, 0)
    HeadstockLayout.SPLIT_4_3 -> HeadstockSides(4, 3)
    HeadstockLayout.INLINE_8 -> HeadstockSides(8, 0)
    HeadstockLayout.SPLIT_4_4 -> HeadstockSides(4, 4)
    HeadstockLayout.INLINE_9 -> HeadstockSides(9, 0)
    HeadstockLayout.SPLIT_5_4 -> HeadstockSides(5, 4)
}

private val HEADSTOCK_TOP_PADDING = 18.dp
private val HEADSTOCK_BOTTOM_PADDING = 14.dp
private val PEG_ROW_HEIGHT = 68.dp
private val PEG_WIDTH = 74.dp
private val PEG_HEIGHT = 56.dp
private val INLINE_PEG_ROW_HEIGHT = 48.dp
private val INLINE_PEG_WIDTH = 64.dp
private val INLINE_PEG_HEIGHT = 48.dp
private val HEADSTOCK_CENTER_WIDTH = 112.dp
private val HEADSTOCK_BODY_HALF_WIDTH = 42.dp
private val HEADSTOCK_NARROW_HALF_WIDTH = 29.dp
private val PEG_STEM_END_OFFSET = HEADSTOCK_CENTER_WIDTH / 2

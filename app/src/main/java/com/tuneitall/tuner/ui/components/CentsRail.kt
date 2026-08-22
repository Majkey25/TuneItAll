package com.tuneitall.tuner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuneitall.tuner.R
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun CentsRail(
    cents: Double?,
    inTune: Boolean,
    idleText: String,
    modifier: Modifier = Modifier,
) {
    val description = when {
        cents == null -> idleText
        inTune -> stringResource(R.string.in_tune)
        cents < 0.0 -> abs(cents).roundToInt().let { value ->
            pluralStringResource(R.plurals.cents_flat, value, value)
        }
        else -> cents.roundToInt().let { value ->
            pluralStringResource(R.plurals.cents_sharp, value, value)
        }
    }
    val railColor = MaterialTheme.colorScheme.onSurfaceVariant
    val markerColor = MaterialTheme.colorScheme.primary
    val displayText = cents?.let { stringResource(R.string.cents_value, it) } ?: idleText

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier.semantics { stateDescription = description },
        ) {
            Text(
                text = displayText,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.testTag("signed_cents"),
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .padding(horizontal = RULER_HORIZONTAL_PADDING)
                    .testTag("cents_rail_canvas"),
            ) {
                val centerY = size.height * 0.55f
                drawLine(
                    color = railColor,
                    start = Offset(0f, centerY),
                    end = Offset(size.width, centerY),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                for (tick in -50..50) {
                    val x = normalizedCentsPosition(tick.toDouble()) * size.width
                    val height = when {
                        tick == 0 -> 28.dp.toPx()
                        tick % 10 == 0 -> 20.dp.toPx()
                        tick % 5 == 0 -> 14.dp.toPx()
                        else -> 8.dp.toPx()
                    }
                    drawLine(
                        color = railColor,
                        start = Offset(x, centerY - height / 2f),
                        end = Offset(x, centerY + height / 2f),
                        strokeWidth = if (tick % 10 == 0) 2.dp.toPx() else 1.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                if (cents != null) {
                    val markerX = normalizedCentsPosition(cents) * size.width
                    drawLine(
                        color = markerColor,
                        start = Offset(markerX, centerY - 14.dp.toPx()),
                        end = Offset(markerX, centerY + 14.dp.toPx()),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Square,
                    )
                }
            }
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .padding(horizontal = RULER_HORIZONTAL_PADDING)
                    .testTag("cents_ruler"),
            ) {
                (-50..50 step 10).forEach { tick ->
                    val x = maxWidth * normalizedCentsPosition(tick.toDouble()) - RULER_LABEL_WIDTH / 2
                    Text(
                        text = if (tick > 0) "+$tick" else "$tick",
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .width(RULER_LABEL_WIDTH)
                            .absoluteOffset(x = x)
                            .testTag("cents_ruler_label_$tick"),
                    )
                }
            }
        }
    }
}

internal fun normalizedCentsPosition(cents: Double): Float {
    require(cents.isFinite()) { "Cents must be finite" }
    return ((cents.coerceIn(MIN_CENTS, MAX_CENTS) - MIN_CENTS) / (MAX_CENTS - MIN_CENTS)).toFloat()
}

private const val MIN_CENTS = -50.0
private const val MAX_CENTS = 50.0
private val RULER_HORIZONTAL_PADDING = 16.dp
private val RULER_LABEL_WIDTH = 32.dp

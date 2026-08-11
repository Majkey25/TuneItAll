package com.tuneitall.tuner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
    val markerColor = if (inTune) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
    val clampedCents = cents?.coerceIn(-50.0, 50.0) ?: 0.0

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.semantics { stateDescription = description },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 8.dp),
        ) {
            val centerY = size.height * 0.58f
            drawLine(
                color = railColor,
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            for (tick in -50..50 step 10) {
                val x = (tick + 50) / 100f * size.width
                val height = if (tick == 0) 22.dp.toPx() else 12.dp.toPx()
                drawLine(
                    color = railColor,
                    start = Offset(x, centerY - height / 2f),
                    end = Offset(x, centerY + height / 2f),
                    strokeWidth = if (tick == 0) 3.dp.toPx() else 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            if (cents != null) {
                val markerX = ((clampedCents + 50.0) / 100.0 * size.width).toFloat()
                drawCircle(
                    color = markerColor,
                    radius = 8.dp.toPx(),
                    center = Offset(markerX, centerY),
                )
            }
        }
        Text(text = description, style = MaterialTheme.typography.labelLarge)
    }
}

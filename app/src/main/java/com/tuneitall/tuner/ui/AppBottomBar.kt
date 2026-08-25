package com.tuneitall.tuner.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuneitall.tuner.R
import java.util.Locale

enum class PrimaryDestination(
    @param:StringRes val labelResource: Int,
) {
    TUNER(R.string.destination_tuner),
    METRONOME(R.string.destination_metronome),
    CHORDS(R.string.destination_chords),
    AUTO_SCROLL(R.string.destination_auto_scroll),
    TRAINER(R.string.destination_trainer),
}

internal data class ResolvedNavigationColors(
    val indicator: Color,
    val icon: Color,
    val text: Color,
)

@Composable
internal fun bottomNavigationSelectedColors(): ResolvedNavigationColors = ResolvedNavigationColors(
    indicator = MaterialTheme.colorScheme.primary,
    icon = MaterialTheme.colorScheme.onPrimary,
    text = MaterialTheme.colorScheme.onBackground,
)

@Composable
fun AppBottomBar(
    selected: PrimaryDestination,
    onSelect: (PrimaryDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedColors = bottomNavigationSelectedColors()
    val unselectedColor = MaterialTheme.colorScheme.onBackground
    BoxWithConstraints(modifier) {
        val labelStyle = if (maxWidth < 400.dp) {
            MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                lineHeight = 11.sp,
                letterSpacing = 0.sp,
            )
        } else {
            MaterialTheme.typography.labelMedium
        }
        NavigationBar {
            PrimaryDestination.entries.forEach { destination ->
                val label = stringResource(destination.labelResource)
                val key = destination.name.lowercase(Locale.ROOT)
                NavigationBarItem(
                    selected = selected == destination,
                    onClick = { onSelect(destination) },
                    icon = { DestinationIcon(destination, key) },
                    label = {
                        Text(
                            text = label,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            style = labelStyle,
                            modifier = Modifier.testTag("bottom_label_$key"),
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = selectedColors.icon,
                        selectedTextColor = selectedColors.text,
                        indicatorColor = selectedColors.indicator,
                        unselectedIconColor = unselectedColor,
                        unselectedTextColor = unselectedColor,
                    ),
                    modifier = Modifier
                        .testTag("bottom_item_$key")
                        .semantics {
                            contentDescription = label
                        },
                )
            }
        }
    }
}

@Composable
private fun DestinationIcon(destination: PrimaryDestination, key: String) {
    val color = LocalContentColor.current
    Canvas(
        modifier = Modifier
            .size(24.dp)
            .testTag("bottom_icon_$key"),
    ) {
        val unit = size.minDimension / ICON_VIEWPORT
        val stroke = Stroke(
            width = 1.8.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun path(block: Path.() -> Unit) = Path().apply(block)

        when (destination) {
            PrimaryDestination.TUNER -> {
                drawPath(
                    path {
                        moveTo(6f * unit, 3f * unit)
                        lineTo(6f * unit, 8f * unit)
                        cubicTo(6f * unit, 11.5f * unit, 8.7f * unit, 14f * unit, 12f * unit, 14f * unit)
                        cubicTo(15.3f * unit, 14f * unit, 18f * unit, 11.5f * unit, 18f * unit, 8f * unit)
                        lineTo(18f * unit, 3f * unit)
                        moveTo(12f * unit, 14f * unit)
                        lineTo(12f * unit, 21f * unit)
                        moveTo(9f * unit, 21f * unit)
                        lineTo(15f * unit, 21f * unit)
                    },
                    color,
                    style = stroke,
                )
            }

            PrimaryDestination.METRONOME -> {
                drawPath(
                    path {
                        moveTo(7f * unit, 21f * unit)
                        lineTo(9f * unit, 4f * unit)
                        lineTo(15f * unit, 4f * unit)
                        lineTo(17f * unit, 21f * unit)
                        close()
                    },
                    color,
                    style = stroke,
                )
                drawLine(color, point(12f, 18f), point(16f, 8f), stroke.width, StrokeCap.Round)
                drawCircle(color, radius = 1.8f * unit, center = point(15.3f, 9.8f))
                drawCircle(color, radius = 1.2f * unit, center = point(12f, 18f))
            }

            PrimaryDestination.CHORDS -> {
                drawRect(
                    color,
                    topLeft = point(5f, 3f),
                    size = Size(14f * unit, 18f * unit),
                    style = stroke,
                )
                listOf(8.5f, 12f, 15.5f).forEach { x ->
                    drawLine(color, point(x, 3f), point(x, 21f), stroke.width, StrokeCap.Round)
                }
                listOf(7.5f, 12f, 16.5f).forEach { y ->
                    drawLine(color, point(5f, y), point(19f, y), stroke.width, StrokeCap.Round)
                }
                drawCircle(color, radius = 1.5f * unit, center = point(8.5f, 9.8f))
                drawCircle(color, radius = 1.5f * unit, center = point(15.5f, 14.2f))
            }

            PrimaryDestination.AUTO_SCROLL -> {
                listOf(5f, 9f, 13f).forEach { y ->
                    drawLine(color, point(5f, y), point(19f, y), stroke.width, StrokeCap.Round)
                }
                drawPath(
                    path {
                        moveTo(12f * unit, 3f * unit)
                        lineTo(12f * unit, 20f * unit)
                        moveTo(8f * unit, 16f * unit)
                        lineTo(12f * unit, 20f * unit)
                        lineTo(16f * unit, 16f * unit)
                    },
                    color,
                    style = stroke,
                )
            }

            PrimaryDestination.TRAINER -> {
                drawPath(
                    path {
                        moveTo(12f * unit, 20f * unit)
                        cubicTo(7.5f * unit, 20f * unit, 4f * unit, 16.8f * unit, 4f * unit, 12f * unit)
                        cubicTo(4f * unit, 7f * unit, 7f * unit, 4f * unit, 11f * unit, 4f * unit)
                        cubicTo(15f * unit, 4f * unit, 17f * unit, 6.5f * unit, 17f * unit, 9.5f * unit)
                        cubicTo(17f * unit, 12f * unit, 15.5f * unit, 13f * unit, 14f * unit, 14f * unit)
                        cubicTo(12.5f * unit, 15f * unit, 12f * unit, 16.5f * unit, 12f * unit, 18f * unit)
                    },
                    color,
                    style = stroke,
                )
                drawPath(
                    path {
                        moveTo(8f * unit, 11f * unit)
                        cubicTo(8f * unit, 8.5f * unit, 10f * unit, 7f * unit, 12f * unit, 7f * unit)
                        cubicTo(14f * unit, 7f * unit, 15f * unit, 8f * unit, 15f * unit, 9.5f * unit)
                    },
                    color,
                    style = stroke,
                )
                drawLine(color, point(19f, 8f), point(21f, 6f), stroke.width, StrokeCap.Round)
                drawLine(color, point(19.5f, 12f), point(22f, 12f), stroke.width, StrokeCap.Round)
            }
        }
    }
}

private const val ICON_VIEWPORT = 24f

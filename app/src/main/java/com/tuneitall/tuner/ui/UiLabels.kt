package com.tuneitall.tuner.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.tuneitall.tuner.R
import com.tuneitall.tuner.model.HeadstockLayout
import com.tuneitall.tuner.model.Instrument

@Composable
fun instrumentName(instrument: Instrument): String = stringResource(
    when (instrument) {
        Instrument.GUITAR -> R.string.instrument_guitar
        Instrument.BASS -> R.string.instrument_bass
        Instrument.UKULELE -> R.string.instrument_ukulele
        Instrument.CHROMATIC -> R.string.mode_chromatic
    },
)

@Composable
fun layoutName(layout: HeadstockLayout): String = when (layout) {
    HeadstockLayout.INLINE_4 -> pluralStringResource(R.plurals.layout_inline, 4, 4)
    HeadstockLayout.SPLIT_2_2 -> "2 + 2"
    HeadstockLayout.SPLIT_3_3 -> "3 + 3"
    HeadstockLayout.INLINE_7 -> pluralStringResource(R.plurals.layout_inline, 7, 7)
    HeadstockLayout.SPLIT_4_3 -> "4 + 3"
    HeadstockLayout.INLINE_8 -> pluralStringResource(R.plurals.layout_inline, 8, 8)
    HeadstockLayout.SPLIT_4_4 -> "4 + 4"
    HeadstockLayout.INLINE_9 -> pluralStringResource(R.plurals.layout_inline, 9, 9)
    HeadstockLayout.SPLIT_5_4 -> "5 + 4"
}

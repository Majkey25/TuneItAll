package com.tuneitall.tuner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.tuneitall.tuner.ui.MusicChoiceGrid
import com.tuneitall.tuner.ui.SettingIcon
import com.tuneitall.tuner.ui.currentPlaneChipColors
import com.tuneitall.tuner.ui.dialogRadioButtonColors
import com.tuneitall.tuner.ui.dialogTextButtonColors
import com.tuneitall.tuner.ui.outlinedFieldColors
import com.tuneitall.tuner.ui.sliderColors
import com.tuneitall.tuner.ui.switchColors
import com.tuneitall.tuner.ui.theme.AppearanceSettings
import com.tuneitall.tuner.ui.theme.ThemePalette
import com.tuneitall.tuner.ui.theme.TuneItAllTheme
import com.tuneitall.tuner.ui.theme.colorContrast
import com.tuneitall.tuner.ui.theme.errorBackgroundColor
import com.tuneitall.tuner.ui.theme.rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ControlPlaneContrastTest {
    @get:Rule val compose = createComposeRule()
    private val settings = AppearanceSettings(lightTheme = ThemePalette.CUSTOM,
        customLight = AppearanceSettings().customLight.copy(background = 0xFFFFFF, surface = 0, text = 0xFFFFFF, muted = 0xFFFFFF))

    @Test fun backgroundControlsUseTheirActualPlane() {
        var chipContrast = 0.0
        var iconContrast = 0.0
        var errorContrast = 0.0
        var inputLabelContrast = 0.0
        compose.setContent {
            TuneItAllTheme(false, settings) {
                val colors = MaterialTheme.colorScheme
                val field = outlinedFieldColors()
                val nativeSelection = LocalTextSelectionColors.current
                Surface(color = colors.background, contentColor = colors.onBackground) {
                    Column {
                        FilterChip(selected = false, onClick = {}, colors = currentPlaneChipColors(),
                            label = { Text("Settings") }, leadingIcon = {
                                val foreground = LocalContentColor.current
                                SideEffect { iconContrast = colorContrast(foreground.rgb(), colors.background.rgb()) }
                                SettingIcon(R.drawable.ic_setting_language)
                            })
                        MusicChoiceGrid(listOf("Choice"), 1, { false }, label = { choice ->
                            val foreground = LocalContentColor.current
                            SideEffect { chipContrast = colorContrast(foreground.rgb(), colors.background.rgb()) }
                            choice
                        }, tag = { "plane_choice" }, onSelected = {})
                        Text("Invalid value", color = errorBackgroundColor(), onTextLayout = {
                            errorContrast = colorContrast(it.layoutInput.style.color.rgb(), colors.background.rgb())
                        })
                        OutlinedTextField("440", {}, colors = field, label = {
                            val foreground = LocalContentColor.current
                            SideEffect { inputLabelContrast = colorContrast(foreground.rgb(), colors.background.rgb()) }
                            Text("Hz")
                        })
                    }
                }
                SideEffect {
                    assertEquals(0xFFFFFF, colors.background.rgb())
                    assertEquals(0, colors.surface.rgb())
                    assertEquals(nativeSelection.handleColor, field.textSelectionColors.handleColor)
                    assertEquals(nativeSelection.backgroundColor, field.textSelectionColors.backgroundColor)
                }
            }
        }
        compose.runOnIdle {
            assertTrue("Settings icon contrast: $iconContrast", iconContrast >= 3.0)
            assertTrue("Background contrast: chip=$chipContrast, error=$errorContrast, inputLabel=$inputLabelContrast",
                chipContrast >= 4.5 && errorContrast >= 4.5 && inputLabelContrast >= 4.5)
        }
    }

    @Test fun dialogActionsUseTheirActualSurfacePlane() {
        var actionContrast = 0.0
        var chipContrast = 0.0
        var handleContrast = 0.0
        compose.setContent {
            TuneItAllTheme(false, settings) {
                val plane = MaterialTheme.colorScheme.surfaceContainerHigh
                val field = outlinedFieldColors(plane, MaterialTheme.colorScheme.onSurface)
                val nativeSelection = LocalTextSelectionColors.current
                AlertDialog(onDismissRequest = {}, title = { Text("Dialog") }, containerColor = plane, tonalElevation = 0.dp,
                    text = {
                        Column {
                            MusicChoiceGrid(listOf("Choice"), 1, { false }, label = { choice ->
                                val foreground = LocalContentColor.current
                                SideEffect { chipContrast = colorContrast(foreground.rgb(), plane.rgb()) }
                                choice
                            }, tag = { "dialog_plane_choice" }, onSelected = {})
                            RadioButton(selected = true, onClick = null, colors = dialogRadioButtonColors(),
                                modifier = Modifier.testTag("dialog_plane_radio"))
                            OutlinedTextField("FFFFFF", {}, colors = field, readOnly = true)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {}, colors = dialogTextButtonColors()) {
                            val foreground = LocalContentColor.current
                            SideEffect { actionContrast = colorContrast(foreground.rgb(), plane.rgb()) }
                            Text("Close")
                        }
                    })
                SideEffect {
                    handleContrast = colorContrast(field.textSelectionColors.handleColor.rgb(), plane.rgb())
                    assertEquals(nativeSelection.backgroundColor, field.textSelectionColors.backgroundColor)
                }
            }
        }
        compose.runOnIdle {
            assertTrue("Dialog contrast: action=$actionContrast, chip=$chipContrast, selectionHandle=$handleContrast",
                actionContrast >= 4.5 && chipContrast >= 4.5 && handleContrast >= 3.0)
        }
        val radioPixels = compose.onNodeWithTag("dialog_plane_radio").captureToImage().toPixelMap()
        val dot = radioPixels[radioPixels.width / 2, radioPixels.height / 2].rgb()
        val plane = radioPixels[0, 0].rgb()
        assertTrue("Selected radio contrast: ${colorContrast(dot, plane)}", colorContrast(dot, plane) >= 3.0)
    }

    @Test fun enabledSurfaceControlsKeepTheirIndicatorsReadable() {
        var thumbContrast = 0.0
        var activeTrackContrast = 0.0
        var switchTrackContrast = 0.0
        var switchThumbContrast = 0.0
        var uncheckedThumbContrast = 0.0
        var uncheckedBorderContrast = 0.0
        compose.setContent {
            TuneItAllTheme(false, settings) {
                val colors = MaterialTheme.colorScheme
                val plane = colors.surfaceContainerLow
                val slider = sliderColors(plane)
                val switch = switchColors(plane)
                Surface(color = plane, contentColor = colors.onSurface) {
                    Column {
                        Slider(50f, {}, colors = slider, valueRange = 0f..100f)
                        Switch(true, null, colors = switch)
                        Switch(false, null, colors = switch)
                    }
                }
                SideEffect {
                    thumbContrast = colorContrast(slider.thumbColor.rgb(), plane.rgb())
                    activeTrackContrast = colorContrast(slider.activeTrackColor.rgb(), plane.rgb())
                    switchTrackContrast = colorContrast(switch.checkedTrackColor.rgb(), plane.rgb())
                    switchThumbContrast = colorContrast(switch.checkedThumbColor.rgb(), switch.checkedTrackColor.rgb())
                    uncheckedThumbContrast = colorContrast(switch.uncheckedThumbColor.rgb(), switch.uncheckedTrackColor.rgb())
                    uncheckedBorderContrast = colorContrast(switch.uncheckedBorderColor.rgb(), plane.rgb())
                }
            }
        }
        compose.runOnIdle {
            assertTrue("Surface indicators: sliderThumb=$thumbContrast, sliderTrack=$activeTrackContrast, " +
                "switchTrack=$switchTrackContrast, switchThumb=$switchThumbContrast, " +
                "uncheckedThumb=$uncheckedThumbContrast, uncheckedBorder=$uncheckedBorderContrast",
                listOf(thumbContrast, activeTrackContrast, switchTrackContrast, switchThumbContrast,
                    uncheckedThumbContrast, uncheckedBorderContrast).all { it >= 3.0 })
        }
    }
}

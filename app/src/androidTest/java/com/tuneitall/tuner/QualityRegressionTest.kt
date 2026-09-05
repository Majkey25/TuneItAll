package com.tuneitall.tuner

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tuneitall.tuner.model.HeadstockLayout
import com.tuneitall.tuner.model.TuningCatalog
import com.tuneitall.tuner.model.TuningPreset
import com.tuneitall.tuner.storage.NoteNotation
import com.tuneitall.tuner.storage.UserPreferences
import com.tuneitall.tuner.ui.AutoScrollScreen
import com.tuneitall.tuner.ui.TunerScreen
import com.tuneitall.tuner.ui.TunerViewModel
import com.tuneitall.tuner.ui.components.Headstock
import com.tuneitall.tuner.ui.theme.TuneItAllTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class QualityRegressionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPreferences() {
        context.getSharedPreferences("tuneitall_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun autoScrollPrimaryActionIsReachableAtTwoHundredPercentFontScale() {
        var opened = false
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(context.resources.displayMetrics.density, fontScale = 2f),
            ) {
                TuneItAllTheme {
                    Box(Modifier.requiredSize(width = 360.dp, height = 720.dp)) {
                        AutoScrollScreen(
                            overlayAllowed = true,
                            accessibilityEnabled = true,
                            disclosureAccepted = true,
                            speed = 12,
                            onSpeedChanged = {},
                            onOpenOverlaySettings = {},
                            onOpenAccessibilitySettings = {},
                            onShowControls = { opened = true },
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("auto_scroll_show_controls").performScrollTo().assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun temporaryMicrophoneDenialCanBeRetried() {
        var retried = false
        val viewModel = TunerViewModel(ApplicationProvider.getApplicationContext())
        composeRule.setContent {
            TuneItAllTheme {
                TunerScreen(
                    state = viewModel.uiState.value.copy(
                        microphoneGranted = false,
                        microphonePermanentlyDenied = false,
                    ),
                    onModeSelected = {},
                    onStringSelected = {},
                    onToggleFavorite = {},
                    onOpenLibrary = {},
                    onOpenSettings = {},
                    onOpenApplicationSettings = {},
                    onRequestMicrophonePermission = { retried = true },
                )
            }
        }

        composeRule.onNodeWithTag("retry_microphone").performScrollTo().assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(retried) }
    }

    @Test
    fun inlineSixHeadstockFitsCompactContentWidth() {
        val tuning = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
        composeRule.setContent {
            TuneItAllTheme {
                Box(Modifier.requiredSize(width = 328.dp, height = 520.dp)) {
                    Headstock(
                        layout = HeadstockLayout.INLINE_6,
                        notes = tuning.notesLowToHigh,
                        selectedIndex = null,
                        confirmed = false,
                        notation = NoteNotation.SHARPS,
                        onStringSelected = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        listOf("String 6 E2", "String 1 E4").forEach { description ->
            val bounds = composeRule.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= root.left)
            assertTrue(bounds.right <= root.right)
        }
    }

    @Test
    fun deletingActiveCustomTuningAlsoRemovesItsFavoriteAndSelectsStandard() {
        val standard = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
        val custom = TuningPreset(
            id = "custom-delete-test",
            name = "Delete test",
            instrument = standard.instrument,
            notesLowToHigh = standard.notesLowToHigh,
            layouts = setOf(HeadstockLayout.SPLIT_3_3),
        )
        val viewModel = TunerViewModel(ApplicationProvider.getApplicationContext<Application>())

        viewModel.saveCustomTuning(custom)
        viewModel.toggleFavorite(custom.id)
        viewModel.deleteCustomTuning(custom.id)

        assertEquals(standard.id, viewModel.uiState.value.tuning.id)
        assertFalse(custom.id in viewModel.uiState.value.favoriteIds)
        assertTrue(viewModel.uiState.value.customTunings.isEmpty())
        assertTrue(UserPreferences(context).customTunings.isEmpty())
    }
}

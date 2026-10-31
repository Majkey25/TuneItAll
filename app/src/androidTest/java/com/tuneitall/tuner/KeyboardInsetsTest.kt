package com.tuneitall.tuner

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import android.graphics.Rect
import android.view.accessibility.AccessibilityWindowInfo
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.ExternalResource

class KeyboardInsetsTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            check(context.packageName.endsWith(".qa"))
            context.getSharedPreferences("tuneitall_preferences", Context.MODE_PRIVATE).edit().clear().commit()
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "pm grant ${context.packageName} ${Manifest.permission.RECORD_AUDIO}",
            )).bufferedReader().use { assertTrue(it.readText().isBlank()) }
            assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        }
    }).around(compose)

    @Test
    fun customTuningLastStringAndSaveRemainReachableWithKeyboardOpen() {
        compose.onNodeWithTag("tuning_picker").performClick()
        compose.onNodeWithText(text(R.string.search_tunings)).performClick()
        requireKeyboard()
        compose.onNodeWithText(text(R.string.create_custom_tuning)).performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText(text(R.string.tuning_name)).performClick().performTextReplacement("Keyboard check")
        compose.onNodeWithText(compose.activity.getString(R.string.string_note, 1))
            .performScrollTo().performClick().performTextReplacement("E4")
        requireKeyboard()
        compose.onNodeWithText(text(R.string.save_and_tune)).performScrollTo().assertIsDisplayed()
        requireKeyboard()
    }

    @Test
    fun referencePitchApplyRemainsReachableWithKeyboardOpen() {
        compose.onNodeWithTag("tuner_settings").performClick()
        compose.onNodeWithTag("settings_reference_pitch").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performScrollTo().performClick().performTextReplacement("444")
        requireKeyboard()
        compose.onNodeWithText(text(R.string.apply)).performScrollTo().assertIsDisplayed()
        requireKeyboard()
    }

    @Test
    fun metronomeControlsAndNavigationRemainAboveKeyboard() {
        compose.onNodeWithTag("bottom_item_metronome").performClick()
        compose.onNodeWithTag("metronome_bpm_input").performClick().performTextReplacement("135")
        requireKeyboard()
        compose.onNodeWithTag("metronome_start_stop").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("bottom_item_tuner").assertIsDisplayed().performClick()
        compose.onNodeWithTag("tuning_picker").assertIsDisplayed()
    }

    @Test
    fun libraryResultsRemainReachableInLandscapeWithKeyboardOpen() {
        val originalOrientation = compose.activity.resources.configuration.orientation
        try {
            compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            compose.waitUntil(5_000) {
                compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
            compose.onNodeWithTag("tuning_picker").performClick()
            compose.onNodeWithText(text(R.string.search_tunings)).performClick()
            requireKeyboard()
            compose.onNodeWithTag("tuning_library")
                .performScrollToNode(hasTestTag("tuning_item_guitar-6-standard"))
            compose.onNodeWithTag("tuning_item_guitar-6-standard").assertIsDisplayed()
            requireKeyboard()
        } finally {
            compose.activity.requestedOrientation = if (originalOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            compose.waitUntil(5_000) { compose.activity.resources.configuration.orientation == originalOrientation }
        }
    }

    @Test
    fun colorDialogApplyIsAboveTheKeyboardAndRespondsToTouch() {
        compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        compose.waitUntil(5_000) {
            compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        }
        compose.onNodeWithTag("tuner_settings").performClick()
        compose.onNodeWithTag("settings_section_general").performClick()
        compose.onNodeWithTag("settings_appearance").performScrollTo().performClick()
        compose.onNodeWithTag("settings_customize_appearance").performScrollTo().performClick()
        compose.onNodeWithTag("appearance_edit_custom").performScrollTo().performClick()
        compose.onNodeWithTag("custom_color_ACCENT").performScrollTo().performClick()
        compose.onNodeWithTag("appearance_color_hex").performClick()

        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val originalInfo = automation.serviceInfo
        val originalFlags = originalInfo.flags
        try {
            automation.serviceInfo = originalInfo.apply {
                flags = originalFlags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            val keyboard = Rect()
            val action = Rect()
            // Traverse virtual Compose nodes rather than relying on provider text search.
            fun findAction(node: AccessibilityNodeInfo?): Rect? {
                if (node == null) return null
                if (node.text?.toString() == text(R.string.apply) && node.isVisibleToUser) {
                    return Rect().also(node::getBoundsInScreen)
                }
                for (index in 0 until node.childCount) {
                    findAction(node.getChild(index))?.let { return it }
                }
                return null
            }
            compose.waitUntil(5_000) {
                val windows = automation.windows
                val ime = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                ime?.getBoundsInScreen(keyboard)
                val button = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                    .mapNotNull { it.root }.filter { it.packageName == compose.activity.packageName }
                    .firstNotNullOfOrNull(::findAction)
                button?.let(action::set)
                ime != null && button != null
            }
            assertTrue("Apply $action overlaps keyboard $keyboard", action.bottom <= keyboard.top)
            compose.onNodeWithTag("appearance_color_apply").assertIsDisplayed().performTouchInput { click() }
            compose.onNodeWithTag("appearance_color_hex").assertDoesNotExist()
        } finally {
            automation.serviceInfo = originalInfo.apply { flags = originalFlags }
        }
    }

    private fun text(id: Int): String = compose.activity.getString(id)

    private fun requireKeyboard() {
        compose.waitUntil(5_000) {
            var visible = false
            compose.runOnIdle {
                visible = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == true
            }
            visible
        }
        compose.runOnIdle {
            assertTrue(ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!
                .getInsets(WindowInsetsCompat.Type.ime()).bottom > 0)
        }
    }
}

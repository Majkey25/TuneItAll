package com.tuneitall.tuner

import android.app.UiAutomation
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.ViewConfiguration
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.tuneitall.tuner.autoscroll.AutoScrollAccessibilityService
import com.tuneitall.tuner.autoscroll.AutoScrollPermissionState
import com.tuneitall.tuner.autoscroll.AutoScrollPreferences
import com.tuneitall.tuner.ui.AutoScrollRoute
import com.tuneitall.tuner.ui.theme.TuneItAllTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AutoScrollStateTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences = AutoScrollPreferences(context)
    private val stored = context.getSharedPreferences("auto_scroll_settings", Context.MODE_PRIVATE)
    private var originalValues: Map<String, *>? = null
    private var gestureService: AutoScrollAccessibilityService? = null

    @Before
    fun prepareQaSpeed() {
        check(context.packageName.endsWith(".qa"))
        originalValues = stored.all.filterKeys { it in TEST_KEYS }
        preferences.speed = 15
        compose.runOnIdle { AutoScrollAccessibilityService.updateSpeed(15) }
    }

    @After
    fun restoreSpeed() {
        gestureService?.let { service ->
            compose.runOnUiThread { service.stop() }
            SystemClock.sleep(GESTURE_SETTLE_MS)
        }
        val saved = originalValues ?: return
        val editor = stored.edit()
        TEST_KEYS.forEach { key ->
            when (val value = saved[key]) {
                null -> editor.remove(key)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> {
                    check(value.all { it is String })
                    editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
                else -> error("Unsupported stored preference type for $key")
            }
        }
        check(editor.commit())
        compose.runOnIdle { AutoScrollAccessibilityService.updateSpeed(preferences.speed) }
    }

    @Test
    fun routeReflectsOverlaySpeedAndPublishesItsOwnChanges() {
        compose.setContent { TuneItAllTheme { AutoScrollRoute() } }
        compose.onNodeWithTag("auto_scroll_speed").assertTextEquals("15")

        compose.runOnIdle {
            preferences.speed = 30
            AutoScrollAccessibilityService.updateSpeed(30)
        }
        compose.onNodeWithTag("auto_scroll_speed").assertTextEquals("30")

        compose.onNodeWithContentDescription(context.getString(R.string.auto_scroll_decrease_speed))
            .performScrollTo().performClick()
        compose.onNodeWithTag("auto_scroll_speed").assertTextEquals("29")
        compose.runOnIdle {
            assertEquals(29, preferences.speed)
            assertEquals(29, AutoScrollAccessibilityService.state.value.speed)
        }
    }

    @Test
    fun wrongTypedPreferencesUseDefaultsWithoutClearingStoredData() {
        check(
            stored.edit().putString("speed", "fast").putInt("disclosure_accepted", 1)
                .putLong("qa_untouched", 77L).commit(),
        )
        assertEquals(15, preferences.speed)
        assertFalse(preferences.disclosureAccepted)

        compose.setContent { TuneItAllTheme { AutoScrollRoute() } }
        compose.onNodeWithTag("auto_scroll_speed").assertTextEquals("15")
        assertEquals("fast", stored.getString("speed", null))
        assertEquals(1, stored.getInt("disclosure_accepted", 0))
        assertEquals(77L, stored.getLong("qa_untouched", 0L))
    }

    @Test
    fun gestureSpeedsScrollLocalTextAndStopAfterSettling() {
        val service = activeGestureService()
        val scroll = showScrollFixture()
        compose.runOnUiThread { assertTrue("Initial gesture was rejected", service.start(1)) }

        listOf(1, 15, 30).forEach { level ->
            compose.runOnUiThread {
                preferences.speed = level
                AutoScrollAccessibilityService.updateSpeed(level)
            }
            SystemClock.sleep(GESTURE_SETTLE_MS)
            val before = compose.runOnUiThread { scroll.value }
            Log.i(LOG_TAG, "speed=$level awaitingMovementFrom=$before")
            compose.waitUntil(timeoutMillis = 5_000L) { scroll.value > before }
            compose.runOnUiThread {
                assertTrue("Scrolling stopped at speed $level", AutoScrollAccessibilityService.state.value.running)
                assertEquals(level, AutoScrollAccessibilityService.state.value.speed)
                Log.i(LOG_TAG, "speed=$level scrollBefore=$before scrollAfter=${scroll.value}")
            }
        }

        compose.runOnUiThread {
            service.stop()
            assertFalse(AutoScrollAccessibilityService.state.value.running)
        }
        SystemClock.sleep(GESTURE_SETTLE_MS)
        val settled = compose.runOnUiThread { scroll.value }
        SystemClock.sleep(GESTURE_SETTLE_MS)
        compose.runOnUiThread {
            assertEquals("A new batch moved content after Stop settled", settled, scroll.value)
            Log.i(LOG_TAG, "stoppedScroll=$settled laterScroll=${scroll.value}")
        }
    }

    @Test
    fun rapidRestartIgnoresThePreviousGesturesCallbacks() {
        val service = activeGestureService()
        val scroll = showScrollFixture()
        compose.runOnUiThread {
            assertTrue("First gesture was rejected", service.start(30))
            service.stop()
            assertTrue("Restart gesture was rejected", service.start(30))
        }
        compose.waitUntil(timeoutMillis = 5_000L) { scroll.value > 0 }
        val first = compose.runOnUiThread { scroll.value }
        SystemClock.sleep(GESTURE_SETTLE_MS)
        compose.runOnUiThread {
            assertTrue("An old callback stopped the restarted run", AutoScrollAccessibilityService.state.value.running)
            assertTrue("The restarted run dispatched no further batch", scroll.value > first)
            Log.i(LOG_TAG, "restartFirstScroll=$first laterScroll=${scroll.value}")
        }
    }

    private fun activeGestureService(): AutoScrollAccessibilityService {
        // UiAutomation otherwise suppresses the service this test needs to exercise.
        InstrumentationRegistry.getInstrumentation()
            .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        assumeTrue(
            "SKIP: Enable only the QA auto-scroll accessibility service through system UI",
            AutoScrollPermissionState.isAccessibilityEnabled(context),
        )
        val connectedBy = SystemClock.uptimeMillis() + 3_000L
        while (AutoScrollAccessibilityService.instance == null && SystemClock.uptimeMillis() < connectedBy) {
            SystemClock.sleep(50L)
        }
        val service = AutoScrollAccessibilityService.instance
        assumeTrue("SKIP: The enabled QA accessibility service did not connect within 3 seconds", service != null)
        val connected = requireNotNull(service)
        gestureService = connected
        compose.runOnUiThread { connected.stop() }
        SystemClock.sleep(GESTURE_SETTLE_MS)
        return connected
    }

    private fun showScrollFixture(): ScrollState {
        val scroll = ScrollState(0)
        compose.setContent {
            TuneItAllTheme {
                Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                    repeat(200) { index ->
                        Text("Local QA row ${index + 1}", Modifier.fillMaxWidth().height(48.dp))
                    }
                }
            }
        }
        Log.i(
            LOG_TAG,
            "heightPx=${context.resources.displayMetrics.heightPixels} touchSlopPx=${ViewConfiguration.get(context).scaledTouchSlop}",
        )
        return scroll
    }

    private companion object {
        val TEST_KEYS = setOf("speed", "disclosure_accepted", "qa_untouched")
        const val GESTURE_SETTLE_MS = 2_250L
        const val LOG_TAG = "AutoScrollLiveQA"
    }
}

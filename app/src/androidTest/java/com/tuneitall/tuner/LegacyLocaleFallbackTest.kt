package com.tuneitall.tuner

import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tuneitall.tuner.storage.UserPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyLocaleFallbackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @After
    fun restoreSystemLanguage() {
        UserPreferences(context).appLanguage = AppLanguage.SYSTEM
    }

    @Test
    fun androidEightThroughTwelveLaunchWithStoredAppLanguage() {
        assumeTrue(Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.TIRAMISU)
        UserPreferences(context).appLanguage = AppLanguage.CZECH

        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            ),
        )
        instrumentation.waitForIdleSync()

        assertEquals("Nastavení", activity.getString(R.string.settings))
        activity.finish()
    }
}

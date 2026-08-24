package com.tuneitall.tuner

import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tuneitall.tuner.autoscroll.AutoScrollAccessibilityService
import com.tuneitall.tuner.autoscroll.AutoScrollOverlayService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class AutoScrollManifestTest {
    @Test
    fun accessibilityServiceCanGestureWithoutReadingWindows() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val service = context.packageManager.getServiceInfo(
            ComponentName(context, AutoScrollAccessibilityService::class.java),
            PackageManager.GET_META_DATA,
        )
        val parser = context.resources.getXml(
            service.metaData.getInt("android.accessibilityservice"),
        )
        parser.use {
            while (parser.eventType != XmlPullParser.START_TAG) parser.next()
            assertTrue(parser.getAttributeBooleanValue(ANDROID_NAMESPACE, "canPerformGestures", false))
            assertFalse(parser.getAttributeBooleanValue(ANDROID_NAMESPACE, "canRetrieveWindowContent", true))
        }
    }

    @Test
    fun overlayUsesOnlyRequiredSpecialForegroundServicePermissions() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val requestedPermissions = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        ).requestedPermissions.orEmpty().toSet()
        assertTrue("android.permission.SYSTEM_ALERT_WINDOW" in requestedPermissions)
        assertTrue("android.permission.FOREGROUND_SERVICE" in requestedPermissions)
        assertTrue("android.permission.FOREGROUND_SERVICE_SPECIAL_USE" in requestedPermissions)

        val service = context.packageManager.getServiceInfo(
            ComponentName(context, AutoScrollOverlayService::class.java),
            PackageManager.GET_META_DATA,
        )
        assertTrue(service.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE != 0)
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}

package com.tuneitall.tuner

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.tuneitall.tuner.ui.AboutScreen
import com.tuneitall.tuner.ui.theme.TuneItAllTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AboutAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun policyLinkOpensThePublishedProjectHub() {
        val opened = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { opened += uri }
            }) {
                TuneItAllTheme { AboutScreen(onBack = {}, onSupport = {}) }
            }
        }

        compose.onNodeWithTag("about_legal_policies").performScrollTo().performClick()
        assertEquals(listOf(POLICIES_URL), opened)
    }

    @Test
    fun privacyExpansionIsAnnouncedAndSurvivesStateRestoration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val restoration = StateRestorationTester(compose)
        restoration.setContent { TuneItAllTheme { AboutScreen(onBack = {}, onSupport = {}) } }
        val collapsed = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(R.string.section_collapsed))
        val expanded = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(R.string.section_expanded))

        compose.onNodeWithTag("about_privacy").assert(collapsed).performClick().assert(expanded)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("about_privacy").assert(expanded).performClick().assert(collapsed)
        compose.onNodeWithTag("about_license").performScrollTo().assert(collapsed).performClick().assert(expanded)
    }
}

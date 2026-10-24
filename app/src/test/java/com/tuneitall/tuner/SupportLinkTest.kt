package com.tuneitall.tuner

import kotlin.test.Test
import kotlin.test.assertEquals

class SupportLinkTest {
    @Test
    fun legalPoliciesUseThisProjectsGitHubPages() {
        assertEquals(
            "https://majkey25.github.io/TuneItAll/${if (SUPPORT_URL.isEmpty()) "play" else "legal"}/",
            POLICIES_URL,
        )
    }

    @Test
    fun supportUsesTheSamePublishedBuyMeACoffeePageAsScanIt() {
        assertEquals(
            if (BuildConfig.DISTRIBUTION_CHANNEL == "github") "https://www.buymeacoffee.com/majkey" else "",
            SUPPORT_URL,
        )
    }
}

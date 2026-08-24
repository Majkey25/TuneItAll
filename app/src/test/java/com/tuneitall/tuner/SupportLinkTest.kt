package com.tuneitall.tuner

import kotlin.test.Test
import kotlin.test.assertEquals

class SupportLinkTest {
    @Test
    fun supportUsesTheSamePublishedBuyMeACoffeePageAsScanIt() {
        assertEquals("https://www.buymeacoffee.com/majkey", SUPPORT_URL)
    }
}

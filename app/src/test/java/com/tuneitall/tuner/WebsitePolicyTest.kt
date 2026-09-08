package com.tuneitall.tuner

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebsitePolicyTest {
    @Test
    fun policyPagesHaveWorkingLocalLinksAndNoEmbeddedTracking() {
        val root = File("../docs").canonicalFile
        val pages = listOf("", "legal", "privacy", "terms", "refunds", "cookies", "support")
        pages.forEach { page ->
            val file = File(File(root, page), "index.html").canonicalFile
            val html = file.readText()
            assertEquals(1, Regex("<h1[ >]").findAll(html).count(), file.path)
            assertTrue(html.contains("href=\"#main-content\""), file.path)
            assertTrue(html.contains("id=\"main-content\""), file.path)
            assertTrue(html.contains("Content-Security-Policy"), file.path)
            assertFalse(Regex("<(script|iframe|form)\\b", RegexOption.IGNORE_CASE).containsMatchIn(html), file.path)
            Regex("<img\\b[^>]*>").findAll(html).forEach { image ->
                assertTrue(image.value.contains("alt=\""), image.value)
            }
            Regex("(?:href|src)=\"([^\"]+)\"").findAll(html).forEach link@ { match ->
                val url = match.groupValues[1]
                if (url.startsWith("https://") || url.startsWith("mailto:")) return@link
                assertFalse(url.contains(":"), url)
                val path = url.substringBefore('#')
                val resolved = if (path.isEmpty()) file else File(file.parentFile, path).canonicalFile
                assertTrue(resolved.toPath().startsWith(root.toPath()), url)
                val target = if (resolved.isDirectory) File(resolved, "index.html") else resolved
                assertTrue(target.isFile, "$page: $url")
                if ('#' in url) assertTrue(target.readText().contains("id=\"${url.substringAfter('#')}\""), url)
            }
        }
    }
}

package com.tuneitall.tuner.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StringNumberTest {
    @Test
    fun `instrument string numbering runs from highest one to lowest string count`() {
        listOf(4, 6, 7, 8, 9).forEach { stringCount ->
            val numbers = List(stringCount) { index -> instrumentStringNumber(index, stringCount) }

            assertEquals((stringCount downTo 1).toList(), numbers)
        }
    }

    @Test
    fun `instrument string numbering rejects an invalid index`() {
        assertFailsWith<IllegalArgumentException> { instrumentStringNumber(-1, 6) }
        assertFailsWith<IllegalArgumentException> { instrumentStringNumber(6, 6) }
    }
}

package com.tuneitall.tuner.metronome

import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MetronomeScheduleTest {
    @Test
    fun `settings validate documented bounds`() {
        assertEquals(20, Bpm(20).value)
        assertEquals(400, Bpm(400).value)
        MetronomeSettings(numerator = 1, denominator = 2, volume = 0)
        MetronomeSettings(numerator = 12, denominator = 16, subdivision = 4, accentEvery = 12, volume = 100, countIn = 4)

        assertFailsWith<IllegalArgumentException> { Bpm(19) }
        assertFailsWith<IllegalArgumentException> { Bpm(401) }
        assertFailsWith<IllegalArgumentException> { MetronomeSettings(numerator = 0) }
        assertFailsWith<IllegalArgumentException> { MetronomeSettings(numerator = 13) }
        assertFailsWith<IllegalArgumentException> { MetronomeSettings(denominator = 3) }
        assertFailsWith<IllegalArgumentException> { MetronomeSettings(subdivision = 0) }
        assertFailsWith<IllegalArgumentException> { MetronomeSettings(subdivision = 5) }
        assertFailsWith<IllegalArgumentException> { MetronomeSettings(accentEvery = 1) }
        assertFailsWith<IllegalArgumentException> { MetronomeSettings(accentEvery = 13) }
        assertFailsWith<IllegalArgumentException> { MetronomeSettings(volume = -1) }
        assertFailsWith<IllegalArgumentException> { MetronomeSettings(volume = 101) }
        assertFailsWith<IllegalArgumentException> { MetronomeSettings(countIn = 3) }
        assertFailsWith<IllegalArgumentException> { MetronomeSchedule(MetronomeSettings(), 26) }
    }

    @Test
    fun `constructor accepts a nonzero schedule origin`() {
        val schedule = MetronomeSchedule(MetronomeSettings(bpm = Bpm(120)), 48_000, startFrame = 500)

        assertEquals(pulse(500, PulseKind.MAIN, musicalBeatIndex = 0, barIndex = 0, beatIndex = 0), schedule.pulsesForBuffer(500, 1).single())
        assertEquals(-1.0, schedule.phaseAt(500), 0.0)
    }

    @Test
    fun `late update waits for the next buffer that contains a main beat`() {
        val schedule = MetronomeSchedule(MetronomeSettings(bpm = Bpm(120)), 48_000)

        schedule.pulsesForBuffer(0, 1)
        schedule.update(MetronomeSettings(bpm = Bpm(60)))
        assertTrue(schedule.pulsesForBuffer(1, 8_192).isEmpty())
        assertTrue(schedule.pulsesForBuffer(8_193, 8_192).isEmpty())
        val boundary = schedule.pulsesForBuffer(16_385, 8_192).single()

        assertEquals(24_000L, boundary.frame)
        assertEquals(0.0, schedule.phaseAt(48_000), 1e-12)
    }

    @Test
    fun `cursor mode rejects buffer after beat cursor without advancing`() {
        val schedule = MetronomeSchedule(MetronomeSettings(bpm = Bpm(120)), 48_000)

        assertEquals(0L, schedule.nextMainBeatFrame())
        assertFailsWith<IllegalStateException> { schedule.pulsesForBuffer(0, 1) }
        assertEquals(24_000L, schedule.nextMainBeatFrame())
    }

    @Test
    fun `cursor mode rejects beat cursor after buffer without advancing`() {
        val schedule = MetronomeSchedule(MetronomeSettings(bpm = Bpm(400)), 48_000)

        schedule.pulsesForBuffer(0, 1)
        assertFailsWith<IllegalStateException> { schedule.nextMainBeatFrame() }

        assertEquals(7_200L, schedule.pulsesForBuffer(1, 8_192).single().frame)
    }

    @Test
    fun `count in uses full bars before live meter and accent restart`() {
        val schedule = MetronomeSchedule(
            MetronomeSettings(bpm = Bpm(60), numerator = 3, accentEvery = 2, countIn = 1),
            48_000,
        )

        val mainPulses = collectBuffers(schedule, 31).filter { it.kind != PulseKind.SUBDIVISION }

        assertEquals(
            listOf(
                pulse(0, PulseKind.MAIN, countInBeatIndex = 0),
                pulse(48_000, PulseKind.MAIN, countInBeatIndex = 1),
                pulse(96_000, PulseKind.MAIN, countInBeatIndex = 2),
                pulse(144_000, PulseKind.ACCENT, musicalBeatIndex = 0, barIndex = 0, beatIndex = 0),
                pulse(192_000, PulseKind.MAIN, musicalBeatIndex = 1, barIndex = 0, beatIndex = 1),
                pulse(240_000, PulseKind.ACCENT, musicalBeatIndex = 2, barIndex = 0, beatIndex = 2),
            ),
            mainPulses.take(6),
        )
        assertEquals(0, schedule.countInBeatsRemaining)
    }

    @Test
    fun `unrelated update keeps remaining count in but count in update restarts it`() {
        val schedule = MetronomeSchedule(MetronomeSettings(bpm = Bpm(60), numerator = 3, countIn = 1), 48_000)

        schedule.pulsesForBuffer(0, 1)
        assertEquals(2, schedule.countInBeatsRemaining)
        schedule.update(MetronomeSettings(bpm = Bpm(120), numerator = 3, countIn = 1))
        collectBuffers(schedule, 6, startFrame = 1)
        assertEquals(1, schedule.countInBeatsRemaining)

        schedule.update(MetronomeSettings(bpm = Bpm(120), numerator = 3, countIn = 2))
        collectBuffers(schedule, 3, startFrame = 49_153)
        assertEquals(5, schedule.countInBeatsRemaining)
    }

    @Test
    fun `accent matrix covers off and every supported interval`() {
        listOf<Int?>(null, 2, 3, 5, 12).forEach { accentEvery ->
            val schedule = MetronomeSchedule(MetronomeSettings(bpm = Bpm(400), accentEvery = accentEvery), 48_000)
            val mains = collectBuffers(schedule, 11).filter { it.kind != PulseKind.SUBDIVISION }.take(13)
            val actual = mains.mapIndexedNotNull { index, pulse -> index.takeIf { pulse.kind == PulseKind.ACCENT } }
            val expected = when (accentEvery) {
                null -> emptyList()
                2 -> listOf(0, 2, 4, 6, 8, 10, 12)
                3 -> listOf(0, 3, 6, 9, 12)
                5 -> listOf(0, 5, 10)
                else -> listOf(0, 12)
            }
            assertEquals(expected, actual)
        }
    }

    @Test
    fun `subdivisions use exact frames without duplicating a main boundary`() {
        val schedule = MetronomeSchedule(MetronomeSettings(bpm = Bpm(120), subdivision = 4), 48_000)
        val pulses = collectBuffers(schedule, 4)
        assertEquals(
            listOf(
                pulse(0, PulseKind.MAIN, musicalBeatIndex = 0, barIndex = 0, beatIndex = 0),
                pulse(6_000, PulseKind.SUBDIVISION, musicalBeatIndex = 0, barIndex = 0, beatIndex = 0),
                pulse(12_000, PulseKind.SUBDIVISION, musicalBeatIndex = 0, barIndex = 0, beatIndex = 0),
                pulse(18_000, PulseKind.SUBDIVISION, musicalBeatIndex = 0, barIndex = 0, beatIndex = 0),
            ),
            pulses.filter { it.musicalBeatIndex == 0L },
        )
        assertEquals(4, pulses.count { it.musicalBeatIndex == 0L })
        val mainFrames = pulses.filter { it.kind != PulseKind.SUBDIVISION }.map { it.frame }.toSet()
        val subdivisionFrames = pulses.filter { it.kind == PulseKind.SUBDIVISION }.map { it.frame }

        assertEquals(subdivisionFrames.size, subdivisionFrames.toSet().size)
        assertTrue(subdivisionFrames.none(mainFrames::contains))
    }

    @Test
    fun `minimum sample rate largest buffer has bounded distinct frames`() {
        val schedule = MetronomeSchedule(MetronomeSettings(bpm = Bpm(400), subdivision = 4), 27)

        val pulses = schedule.pulsesForBuffer(0, 8_192)

        assertTrue(pulses.isNotEmpty())
        assertTrue(pulses.size <= 8_192)
        assertEquals(pulses.size, pulses.map { it.frame }.toSet().size)
        assertTrue(pulses.all { it.frame in 0L..8_191L })
    }

    @Test
    fun `production buffers preserve fractional tempo over ten minutes`() {
        listOf(20, 137, 400).forEach { bpm ->
            val schedule = MetronomeSchedule(MetronomeSettings(bpm = Bpm(bpm)), 48_000)
            var startFrame = 0L
            var mainBeatCount = 0
            var actual = 0L
            val beats = bpm * 10

            while (mainBeatCount < beats) {
                schedule.pulsesForBuffer(startFrame, 8_192).forEach { pulse ->
                    if (pulse.kind != PulseKind.SUBDIVISION && mainBeatCount < beats) {
                        actual = pulse.frame
                        mainBeatCount++
                    }
                }
                startFrame += 8_192
            }

            val expected = ((beats - 1) * 48_000.0 * 60.0 / bpm).roundToLong()
            assertTrue(abs(actual - expected) <= 1L, "$bpm BPM: actual=$actual expected=$expected")
        }
    }

    @Test
    fun `same BPM updates preserve fractional frame remainder`() {
        val base = MetronomeSettings(bpm = Bpm(137))
        val schedule = MetronomeSchedule(base, 48_000)
        var startFrame = 0L
        var mainBeatCount = 0
        var actual = 0L

        while (mainBeatCount < 1_370) {
            schedule.update(
                base.copy(
                    accentEvery = if (mainBeatCount % 2 == 0) null else 5,
                    sound = if (mainBeatCount % 2 == 0) MetronomeSound.WOOD else MetronomeSound.CLICK,
                    volume = if (mainBeatCount % 2 == 0) 20 else 80,
                ),
            )
            schedule.pulsesForBuffer(startFrame, 8_192).forEach { pulse ->
                if (pulse.kind != PulseKind.SUBDIVISION && mainBeatCount < 1_370) {
                    actual = pulse.frame
                    mainBeatCount++
                }
            }
            startFrame += 8_192
        }

        assertEquals((1_369 * 48_000.0 * 60.0 / 137).roundToLong(), actual)
    }

    @Test
    fun `phase uses actual fractional intervals and keeps parity through update`() {
        val schedule = MetronomeSchedule(MetronomeSettings(bpm = Bpm(137)), 48_000)

        schedule.pulsesForBuffer(0, 8_192)
        assertEquals(-1.0, schedule.phaseAt(0), 1e-12)
        assertTrue(schedule.phaseAt(10_510) < 0.0)
        assertTrue(schedule.phaseAt(10_511) > 0.0)
        assertEquals(1.0, schedule.phaseAt(21_021), 1e-12)

        schedule.update(MetronomeSettings(bpm = Bpm(120)))
        collectBuffers(schedule, 3, startFrame = 8_192)
        assertEquals(1.0, schedule.phaseAt(21_021), 1e-12)
        assertEquals(0.0, schedule.phaseAt(33_021))
        assertEquals(-1.0, schedule.phaseAt(45_021), 1e-12)
        assertFailsWith<IllegalArgumentException> { schedule.phaseAt(-1) }
    }

    @Test
    fun `phase rejects an interval evicted from bounded history`() {
        val schedule = MetronomeSchedule(MetronomeSettings(bpm = Bpm(400)), 48_000)

        collectBuffers(schedule, 8)

        assertFailsWith<IllegalArgumentException> { schedule.phaseAt(0) }
    }

    @Test
    fun `overlap and gap fail without moving the production cursor`() {
        val schedule = MetronomeSchedule(MetronomeSettings(bpm = Bpm(400)), 48_000)

        schedule.pulsesForBuffer(0, 1)
        assertFailsWith<IllegalArgumentException> { schedule.pulsesForBuffer(0, 1) }
        assertFailsWith<IllegalArgumentException> { schedule.pulsesForBuffer(2, 1) }

        assertEquals(7_200L, schedule.pulsesForBuffer(1, 8_192).single().frame)
    }

    @Test
    fun `numerator update restarts an active count in`() {
        val schedule = MetronomeSchedule(MetronomeSettings(bpm = Bpm(60), numerator = 3, countIn = 1), 48_000)

        schedule.pulsesForBuffer(0, 1)
        schedule.update(MetronomeSettings(bpm = Bpm(60), numerator = 4, countIn = 1))
        collectBuffers(schedule, 6, startFrame = 1)

        assertEquals(3, schedule.countInBeatsRemaining)
    }

    @Test
    fun `numerator update does not restart a completed count in`() {
        val schedule = MetronomeSchedule(MetronomeSettings(bpm = Bpm(60), numerator = 1, countIn = 1), 48_000)

        schedule.pulsesForBuffer(0, 1)
        schedule.update(MetronomeSettings(bpm = Bpm(60), numerator = 4, countIn = 1))
        collectBuffers(schedule, 6, startFrame = 1)

        assertEquals(0, schedule.countInBeatsRemaining)
    }

    @Test
    fun `invalid first buffer calls do not select a cursor mode`() {
        val gap = MetronomeSchedule(MetronomeSettings(), 48_000)
        assertFailsWith<IllegalArgumentException> { gap.pulsesForBuffer(0, 0) }
        assertFailsWith<IllegalArgumentException> { gap.pulsesForBuffer(0, 8_193) }
        assertFailsWith<IllegalArgumentException> { gap.pulsesForBuffer(1, 1) }
        assertEquals(0L, gap.nextMainBeatFrame())

        val overflow = MetronomeSchedule(MetronomeSettings(), 48_000, Long.MAX_VALUE - 1)
        assertFailsWith<ArithmeticException> { overflow.pulsesForBuffer(Long.MAX_VALUE - 1, 2) }

        val nearEnd = Long.MAX_VALUE - 10
        val beatCursor = MetronomeSchedule(MetronomeSettings(bpm = Bpm(400)), 27, nearEnd)
        assertFailsWith<ArithmeticException> { beatCursor.pulsesForBuffer(nearEnd, 100) }
        assertEquals(nearEnd, beatCursor.nextMainBeatFrame())
    }

    @Test
    fun `checked frame arithmetic rejects overflow`() {
        val startFrame = Long.MAX_VALUE - 1
        val schedule = MetronomeSchedule(MetronomeSettings(), 48_000, startFrame)

        assertFailsWith<ArithmeticException> { schedule.pulsesForBuffer(startFrame, 1) }
        assertFailsWith<ArithmeticException> {
            MetronomeSchedule(MetronomeSettings(), 48_000, Long.MAX_VALUE).pulsesForBuffer(Long.MAX_VALUE, 1)
        }
    }

    @Test
    fun `scheduled pulse rejects invalid shapes and indices`() {
        assertFailsWith<IllegalArgumentException> { pulse(-1, PulseKind.MAIN, musicalBeatIndex = 0, barIndex = 0, beatIndex = 0) }
        assertFailsWith<IllegalArgumentException> { pulse(0, PulseKind.MAIN) }
        assertFailsWith<IllegalArgumentException> { pulse(0, PulseKind.MAIN, countInBeatIndex = 0, musicalBeatIndex = 0, barIndex = 0, beatIndex = 0) }
        assertFailsWith<IllegalArgumentException> { pulse(0, PulseKind.MAIN, musicalBeatIndex = 0, barIndex = -1, beatIndex = 0) }
        assertFailsWith<IllegalArgumentException> { pulse(0, PulseKind.MAIN, countInBeatIndex = -1) }
        assertFailsWith<IllegalArgumentException> { pulse(0, PulseKind.MAIN, musicalBeatIndex = -1, barIndex = 0, beatIndex = 0) }
        assertFailsWith<IllegalArgumentException> { pulse(0, PulseKind.MAIN, musicalBeatIndex = 0, barIndex = 0, beatIndex = -1) }
    }

    private fun collectBuffers(schedule: MetronomeSchedule, count: Int, startFrame: Long = 0L): List<ScheduledPulse> = buildList {
        var nextFrame = startFrame
        repeat(count) {
            addAll(schedule.pulsesForBuffer(nextFrame, 8_192))
            nextFrame += 8_192
        }
    }

    private fun pulse(
        frame: Long,
        kind: PulseKind,
        countInBeatIndex: Int? = null,
        musicalBeatIndex: Long? = null,
        barIndex: Long? = null,
        beatIndex: Int? = null,
    ) = ScheduledPulse(frame, kind, countInBeatIndex, musicalBeatIndex, barIndex, beatIndex)
}

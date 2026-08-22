package com.tuneitall.tuner.metronome

import java.util.ArrayDeque
import kotlin.math.PI
import kotlin.math.cos

enum class PulseKind {
    MAIN,
    ACCENT,
    SUBDIVISION,
}

data class ScheduledPulse(
    val frame: Long,
    val kind: PulseKind,
    val countInBeatIndex: Int?,
    val musicalBeatIndex: Long?,
    val barIndex: Long?,
    val beatIndex: Int?,
) {
    init {
        require(frame >= 0L)
        require(countInBeatIndex == null || countInBeatIndex >= 0)
        require(musicalBeatIndex == null || musicalBeatIndex >= 0L)
        require(barIndex == null || barIndex >= 0L)
        require(beatIndex == null || beatIndex >= 0)
        require(
            (countInBeatIndex != null && musicalBeatIndex == null && barIndex == null && beatIndex == null) ||
                (countInBeatIndex == null && musicalBeatIndex != null && barIndex != null && beatIndex != null),
        )
    }

    val isCountIn: Boolean
        get() = countInBeatIndex != null
}

class MetronomeSchedule(
    initialSettings: MetronomeSettings,
    private val sampleRate: Int,
    startFrame: Long = 0L,
) {
    private var settings = initialSettings
    private var pendingSettings: MetronomeSettings? = null
    private var mode = ConsumptionMode.NONE
    private var nextBufferStart = startFrame
    private var nextMainFrame = startFrame
    private var frameRemainder = 0L
    private var currentBeat: BeatInterval? = null
    private var currentBeatRemainder = 0L
    private var currentBeatIsCountIn = false
    private var nextPulseIndex = 0
    private var nextBeatParity = false
    private var countInRemaining = initialSettings.countIn * initialSettings.numerator
    private var countInBeatIndex = 0
    private var musicalBeatIndex = 0L
    private val phaseHistory = ArrayDeque<BeatInterval>(MAX_PHASE_INTERVALS)

    val countInBeatsRemaining: Int
        get() = countInRemaining

    init {
        require(sampleRate >= MIN_SAMPLE_RATE)
        require(startFrame >= 0L)
    }

    fun pulsesForBuffer(startFrame: Long, frameCount: Int): List<ScheduledPulse> {
        require(startFrame >= 0L)
        require(frameCount in 1..MAX_BUFFER_FRAMES)
        require(startFrame == nextBufferStart)
        val endFrame = Math.addExact(startFrame, frameCount.toLong())
        selectMode(ConsumptionMode.BUFFER)
        val pulses = ArrayList<ScheduledPulse>(settings.subdivision + 1)

        while (true) {
            if (currentBeat == null && nextMainFrame >= endFrame) break
            beginCurrentBeat()
            val frame = pulseFrame()
            if (frame >= endFrame) break
            pulses += pulseAt(frame)
            nextPulseIndex++
            if (nextPulseIndex == settings.subdivision) finishCurrentBeat()
        }
        nextBufferStart = endFrame
        return pulses
    }

    internal fun nextMainBeatFrame(): Long {
        selectMode(ConsumptionMode.BEAT)
        beginCurrentBeat()
        val frame = requireNotNull(currentBeat).startFrame
        finishCurrentBeat()
        return frame
    }

    fun phaseAt(frame: Long): Double {
        require(frame >= scheduleOriginFrame)
        val oldest = phaseHistory.peekFirst() ?: throw IllegalArgumentException("No scheduled interval")
        require(frame >= oldest.startFrame)
        val interval = phaseHistory.firstOrNull { frame in it.startFrame..it.endFrame }
            ?: throw IllegalArgumentException("Frame is outside scheduled intervals")
        val endpointAtStart = if (interval.parity) 1.0 else -1.0
        if (frame == interval.startFrame) return endpointAtStart
        if (frame == interval.endFrame) return -endpointAtStart
        val duration = interval.endFrame - interval.startFrame
        if (duration % 2L == 0L && frame - interval.startFrame == duration / 2L) return 0.0
        val progress = (frame - interval.startFrame).toDouble() / (interval.endFrame - interval.startFrame)
        return endpointAtStart * cos(PI * progress)
    }

    fun update(settings: MetronomeSettings) {
        pendingSettings = settings
    }

    private fun beginCurrentBeat() {
        if (currentBeat != null) return
        applyPendingSettings()
        val accumulatedFrames = Math.addExact(frameRemainder, framesPerMinute)
        val endFrame = Math.addExact(nextMainFrame, accumulatedFrames / settings.bpm.value)
        currentBeatRemainder = accumulatedFrames % settings.bpm.value
        currentBeat = BeatInterval(nextMainFrame, endFrame, nextBeatParity)
        currentBeatIsCountIn = countInRemaining > 0
        nextPulseIndex = 0
        phaseHistory += requireNotNull(currentBeat)
        if (phaseHistory.size > MAX_PHASE_INTERVALS) phaseHistory.removeFirst()
    }

    private fun pulseFrame(): Long {
        val beat = requireNotNull(currentBeat)
        if (nextPulseIndex == 0) return beat.startFrame
        val offset = Math.multiplyExact(beat.endFrame - beat.startFrame, nextPulseIndex.toLong()) / settings.subdivision
        return Math.addExact(beat.startFrame, offset)
    }

    private fun pulseAt(frame: Long): ScheduledPulse {
        if (currentBeatIsCountIn) {
            return ScheduledPulse(frame, if (nextPulseIndex == 0) PulseKind.MAIN else PulseKind.SUBDIVISION, countInBeatIndex, null, null, null)
        }
        return ScheduledPulse(
            frame = frame,
            kind = if (nextPulseIndex == 0) mainPulseKind() else PulseKind.SUBDIVISION,
            countInBeatIndex = null,
            musicalBeatIndex = musicalBeatIndex,
            barIndex = musicalBeatIndex / settings.numerator,
            beatIndex = (musicalBeatIndex % settings.numerator).toInt(),
        )
    }

    private fun mainPulseKind(): PulseKind {
        val accentEvery = settings.accentEvery
        return if (accentEvery != null && musicalBeatIndex % accentEvery == 0L) PulseKind.ACCENT else PulseKind.MAIN
    }

    private fun finishCurrentBeat() {
        val beat = requireNotNull(currentBeat)
        if (currentBeatIsCountIn) {
            countInRemaining--
            countInBeatIndex++
        } else {
            musicalBeatIndex++
        }
        nextMainFrame = beat.endFrame
        frameRemainder = currentBeatRemainder
        nextBeatParity = !beat.parity
        currentBeat = null
        nextPulseIndex = 0
    }

    private fun applyPendingSettings() {
        val updated = pendingSettings ?: return
        val restartCountIn = updated.countIn != settings.countIn ||
            (countInRemaining > 0 && updated.numerator != settings.numerator)
        if (restartCountIn) {
            countInRemaining = updated.countIn * updated.numerator
            countInBeatIndex = 0
        }
        if (updated.bpm != settings.bpm) frameRemainder = 0L
        settings = updated
        pendingSettings = null
    }

    private fun selectMode(requested: ConsumptionMode) {
        if (mode == ConsumptionMode.NONE) {
            mode = requested
            return
        }
        check(mode == requested) { "MetronomeSchedule uses $mode cursor" }
    }

    private val framesPerMinute = sampleRate.toLong() * 60L

    private data class BeatInterval(
        val startFrame: Long,
        val endFrame: Long,
        val parity: Boolean,
    )

    private enum class ConsumptionMode {
        NONE,
        BUFFER,
        BEAT,
    }

    private companion object {
        const val MAX_BUFFER_FRAMES = 8_192
        const val MAX_PHASE_INTERVALS = 8
        const val MIN_SAMPLE_RATE = 27
    }

    private val scheduleOriginFrame = startFrame
}

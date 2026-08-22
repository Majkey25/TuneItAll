package com.tuneitall.tuner.ui

import com.tuneitall.tuner.audio.TunerProfile
import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.model.TuningCatalog
import com.tuneitall.tuner.storage.NoteNotation
import com.tuneitall.tuner.tuner.TunerMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DetectionContextTest {
    private val standard = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
    private val dropD = requireNotNull(TuningCatalog.byId("guitar-6-drop-d"))
    private val state = TunerUiState(
        mode = TunerMode.AUTO,
        tuning = standard,
        selectedString = 0,
        headstockLayout = standard.layouts.first(),
        referencePitch = ReferencePitch(440.0),
        notation = NoteNotation.SHARPS,
        favoriteIds = emptySet(),
        customTunings = emptyList(),
        reading = null,
        microphoneGranted = true,
        microphonePermanentlyDenied = false,
        listening = true,
        referenceTonePlaying = false,
        error = null,
        audioSettings = TunerProfile.BALANCED.settings,
    )

    @Test
    fun `detection context rejects every audio-affecting change`() {
        assertTrue(sameDetectionContext(state, state))
        assertFalse(sameDetectionContext(state, state.copy(mode = TunerMode.MANUAL)))
        assertFalse(sameDetectionContext(state, state.copy(tuning = dropD)))
        assertFalse(sameDetectionContext(state, state.copy(selectedString = 1)))
        assertFalse(sameDetectionContext(state, state.copy(referencePitch = ReferencePitch(441.0))))
        assertFalse(
            sameDetectionContext(
                state,
                state.copy(audioSettings = state.audioSettings.copy(noiseRejection = 31)),
            ),
        )
    }

    @Test
    fun `equal fields with a new session or revision are stale`() {
        val captured = DetectionCallbackToken(
            state = state,
            audioSessionGeneration = 10L,
            detectionContextRevision = 20L,
        )

        assertTrue(isCurrentDetectionCallback(captured, state, 10L, 20L))
        assertFalse(isCurrentDetectionCallback(captured, state, 11L, 20L))
        assertFalse(isCurrentDetectionCallback(captured, state, 10L, 21L))
    }

    @Test
    fun `generation change blocks downstream mutation when fields are equal`() {
        val captured = DetectionCallbackToken(
            state = state,
            audioSessionGeneration = 10L,
            detectionContextRevision = 20L,
        )
        var downstreamMutated = false

        val applied = mutateIfCurrentDetectionCallback(captured, state, 11L, 20L) {
            downstreamMutated = true
        }

        assertFalse(applied)
        assertFalse(downstreamMutated)
    }
}

package com.tuneitall.tuner.music

data class ChordEvaluation(
    val rootWcsr: Double,
    val majorMinorWcsr: Double,
    val qualityWcsr: Double,
    val segmentationScore: Double,
    val medianBoundaryErrorMillis: Double,
    val coverage: Double,
) {
    init {
        require(rootWcsr in 0.0..1.0)
        require(majorMinorWcsr in 0.0..1.0)
        require(qualityWcsr in 0.0..1.0)
        require(segmentationScore in 0.0..1.0)
        require(medianBoundaryErrorMillis >= 0.0)
        require(coverage in 0.0..1.0)
    }
}

fun evaluateChords(
    reference: List<ChordEvent>,
    estimate: List<ChordEvent>,
    songEndMillis: Long,
): ChordEvaluation {
    require(songEndMillis > 0L)
    validateTimeline(reference, songEndMillis)
    validateTimeline(estimate, songEndMillis)
    val referenceDuration = reference.sumOf(ChordEvent::durationMillis)
    require(referenceDuration > 0L)
    val majorMinorDuration = reference.filter { majorMinorClass(it.chord) != null }.sumOf(ChordEvent::durationMillis)
    return ChordEvaluation(
        rootWcsr = matchingOverlap(reference, estimate) { expected, actual ->
            expected.rootPitchClass == actual.rootPitchClass
        } / referenceDuration,
        majorMinorWcsr = if (majorMinorDuration == 0L) 0.0 else {
            matchingOverlap(reference, estimate) { expected, actual ->
                val expectedClass = majorMinorClass(expected)
                expected.rootPitchClass == actual.rootPitchClass &&
                    expectedClass != null && expectedClass == majorMinorClass(actual)
            } / majorMinorDuration
        },
        qualityWcsr = matchingOverlap(reference, estimate) { expected, actual ->
            expected.rootPitchClass == actual.rootPitchClass && expected.quality == actual.quality
        } / referenceDuration,
        segmentationScore = (
            1.0 - maxOf(
                directionalSegmentationLoss(reference, estimate),
                directionalSegmentationLoss(estimate, reference),
            ) / songEndMillis
            ).coerceIn(0.0, 1.0),
        medianBoundaryErrorMillis = medianBoundaryError(reference, estimate, songEndMillis),
        coverage = estimate.sumOf(ChordEvent::durationMillis).toDouble() / songEndMillis,
    )
}

private fun validateTimeline(events: List<ChordEvent>, songEndMillis: Long) {
    var previousEnd = 0L
    events.forEach { event ->
        require(event.startMillis >= previousEnd)
        require(event.endMillis <= songEndMillis)
        previousEnd = event.endMillis
    }
}

private fun matchingOverlap(
    reference: List<ChordEvent>,
    estimate: List<ChordEvent>,
    matches: (Chord, Chord) -> Boolean,
): Double {
    var referenceIndex = 0
    var estimateIndex = 0
    var total = 0L
    while (referenceIndex < reference.size && estimateIndex < estimate.size) {
        val expected = reference[referenceIndex]
        val actual = estimate[estimateIndex]
        if (matches(expected.chord, actual.chord)) total += overlap(expected, actual)
        if (expected.endMillis <= actual.endMillis) referenceIndex++ else estimateIndex++
    }
    return total.toDouble()
}

// ponytail: evaluation lists are small; replace with an interval sweep only for corpus-scale metrics.
private fun directionalSegmentationLoss(source: List<ChordEvent>, target: List<ChordEvent>): Double =
    source.sumOf { segment ->
        segment.durationMillis - target.maxOfOrNull { overlap(segment, it) }.orZero()
    }.toDouble()

private fun medianBoundaryError(
    reference: List<ChordEvent>,
    estimate: List<ChordEvent>,
    songEndMillis: Long,
): Double {
    val expected = reference.map(ChordEvent::endMillis).filter { it < songEndMillis }
    if (expected.isEmpty()) return 0.0
    val actual = estimate.map(ChordEvent::endMillis).filter { it < songEndMillis }
    if (actual.isEmpty()) return songEndMillis.toDouble()
    val errors = expected.map { boundary -> actual.minOf { kotlin.math.abs(it - boundary) } }.sorted()
    val middle = errors.size / 2
    return if (errors.size % 2 == 1) errors[middle].toDouble() else (errors[middle - 1] + errors[middle]) / 2.0
}

private fun overlap(first: ChordEvent, second: ChordEvent): Long =
    (minOf(first.endMillis, second.endMillis) - maxOf(first.startMillis, second.startMillis)).coerceAtLeast(0L)

private fun majorMinorClass(chord: Chord): Int? = when (chord.quality) {
    ChordQuality.MAJOR,
    ChordQuality.MAJOR_SIXTH,
    ChordQuality.DOMINANT_SEVENTH,
    ChordQuality.MAJOR_SEVENTH,
    ChordQuality.ADD_NINTH,
    -> 0

    ChordQuality.MINOR,
    ChordQuality.MINOR_SIXTH,
    ChordQuality.MINOR_SEVENTH,
    ChordQuality.MINOR_ADD_NINTH,
    -> 1

    ChordQuality.SUSPENDED_SECOND,
    ChordQuality.SUSPENDED_FOURTH,
    ChordQuality.DIMINISHED,
    ChordQuality.AUGMENTED,
    ChordQuality.HALF_DIMINISHED_SEVENTH,
    ChordQuality.POWER,
    -> null
}

private fun Long?.orZero(): Long = this ?: 0L

package com.tempotunestudio.processing

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.pow

/**
 * Tests the semitone → pitch-factor conversion used in both
 * VideoProcessor (export) and EditorFragment (live preview).
 *
 * Formula: pitchFactor = 2^(semitones / 12)
 */
class PitchCalculationTest {

    private fun semitonesToFactor(semitones: Float): Float =
        2.0.pow(semitones / 12.0).toFloat()

    @Test
    fun `0 semitones gives factor of 1 (no change)`() {
        assertEquals(1.0f, semitonesToFactor(0f), 0.0001f)
    }

    @Test
    fun `+12 semitones gives factor of 2 (one octave up)`() {
        assertEquals(2.0f, semitonesToFactor(12f), 0.0001f)
    }

    @Test
    fun `-12 semitones gives factor of 0_5 (one octave down)`() {
        assertEquals(0.5f, semitonesToFactor(-12f), 0.0001f)
    }

    @Test
    fun `+7 semitones gives factor for perfect fifth`() {
        // 2^(7/12) ≈ 1.4983
        assertEquals(1.4983f, semitonesToFactor(7f), 0.001f)
    }

    @Test
    fun `-5 semitones gives factor for perfect fourth down`() {
        // 2^(-5/12) ≈ 0.7491
        assertEquals(0.7491f, semitonesToFactor(-5f), 0.001f)
    }

    @Test
    fun `pitch factor is always positive`() {
        listOf(-12f, -6f, 0f, 6f, 12f).forEach { semitones ->
            val factor = semitonesToFactor(semitones)
            assert(factor > 0f) { "Expected positive factor for $semitones semitones, got $factor" }
        }
    }
}

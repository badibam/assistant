package com.assistant.core.ui

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The arithmetic of a slider that stops every `step` from `min`.
 *
 * Compose's Slider works in Float and hands back positions like 0.30000001; every value here
 * goes through BigDecimal built from the decimal text of the number, so a slider from 0 by 0.1
 * gives back 0.3 and not a neighbour of it.
 */
object SliderSteps {

    /** The last value the slider can reach: `max` itself when the step falls on it, the stop before it otherwise. */
    fun lastStop(min: Double, max: Double, step: Double): Double =
        exact(min).add(exact(step).multiply(BigDecimal(intervals(min, max, step)))).toDouble()

    /** The stops strictly between the two ends, which is what Compose's Slider calls `steps`. */
    fun innerStops(min: Double, max: Double, step: Double): Int =
        maxOf(intervals(min, max, step) - 1, 0)

    /** The stop nearest to a raw slider position. */
    fun snap(raw: Double, min: Double, step: Double): Double {
        val stepsFromMin = exact(raw).subtract(exact(min)).divide(exact(step), 0, RoundingMode.HALF_UP)
        return exact(min).add(stepsFromMin.multiply(exact(step))).toDouble()
    }

    /** How many decimals a stop can carry: 0 from 1 by 1, 1 from 0 by 0.5, 2 from 0.25 by 0.25. */
    fun decimals(min: Double, step: Double): Int =
        maxOf(exact(min).stripTrailingZeros().scale(), exact(step).stripTrailingZeros().scale(), 0)

    private fun intervals(min: Double, max: Double, step: Double): Int =
        exact(max).subtract(exact(min)).divide(exact(step), 0, RoundingMode.DOWN).toInt()

    private fun exact(value: Double): BigDecimal = BigDecimal(value.toString())
}

package app.treelune.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the slider's arithmetic: which values it stops on, and that a Float position from
 * Compose comes back as the exact decimal stop.
 */
class SliderStepsTest {

    /** Whole numbers one by one: the stops the settings sliders have always had. */
    @Test
    fun wholeNumbers_stopOnEachOne() {
        assertEquals(10.0, SliderSteps.lastStop(1.0, 10.0, 1.0), 0.0)
        assertEquals(8, SliderSteps.innerStops(1.0, 10.0, 1.0))
        assertEquals(0, SliderSteps.decimals(1.0, 1.0))
        assertEquals(0, SliderSteps.innerStops(0.0, 1.0, 1.0))
    }

    /** Half points: 0 to 5 has eleven stops, nine of them between the ends, shown with one decimal. */
    @Test
    fun halfPoints_stopEveryHalf() {
        assertEquals(9, SliderSteps.innerStops(0.0, 5.0, 0.5))
        assertEquals(1, SliderSteps.decimals(0.0, 0.5))
        assertEquals(2, SliderSteps.decimals(0.25, 0.25))
    }

    /** A Float position lands on the exact stop, not on a binary neighbour of it. */
    @Test
    fun aFloatPosition_snapsToTheExactStop() {
        assertEquals(0.3, SliderSteps.snap(0.3f.toDouble(), 0.0, 0.1), 0.0)
        assertEquals(3.5, SliderSteps.snap(3.4f.toDouble(), 0.0, 0.5), 0.0)
        assertEquals(7.0, SliderSteps.snap(6.9999f.toDouble(), 1.0, 1.0), 0.0)
        assertEquals(-2.0, SliderSteps.snap(-2.2, -5.0, 1.0), 0.0)
    }

    /** A max the step does not fall on ends the track on the stop before it. */
    @Test
    fun aMaxOffTheStep_endsOnTheStopBefore() {
        assertEquals(9.0, SliderSteps.lastStop(1.0, 10.0, 2.0), 0.0)
        assertEquals(3, SliderSteps.innerStops(1.0, 9.0, 2.0))
    }
}
